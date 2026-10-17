import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Guard against silently redesigning the imported framework picker again.
const root = fileURLToPath(new URL('../', import.meta.url));
const framework = process.argv[2] ?? 'C:/IDE/Android/liquidglass_sketch';
const original = name => readFileSync(resolve(framework, 'sample/src/main/java/dev/liquidglass/sample', name), 'utf8').replaceAll('\r\n', '\n');
const port = name => readFileSync(resolve(root, 'app/src/main/java/dev/glass/soundbar', name), 'utf8')
    .replaceAll('\r\n', '\n').replace('package dev.glass.soundbar', 'package dev.liquidglass.sample');
const source = original('Ios27ColorPickerDialog.kt');
let picker = port('FrameworkColorPicker.kt');
// Keep every grid cell square while retaining the framework's 296dp mode footprint.
const squareGrid = `    Box(Modifier.fillMaxWidth().height(296.dp), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.widthIn(max = 296.dp * PickerGridColumns / PickerGridRows).fillMaxWidth()
            .aspectRatio(PickerGridColumns.toFloat() / PickerGridRows)`;
assert.ok(picker.includes(squareGrid), 'Grid cells must scale together in both dimensions');
picker = picker.replace(squareGrid, '    Column(\n        Modifier.fillMaxWidth().height(296.dp)');
picker = picker.replace('    }\n}\n\nprivate fun colorToHsv', '}\n\nprivate fun colorToHsv');
for (const width of [240, 272, 312, 354, 600]) {
    const gridWidth = Math.min(width, 296 * 12 / 10);
    const gridHeight = gridWidth * 10 / 12;
    assert.ok(Math.abs(gridWidth / 12 - gridHeight / 10) < 1e-9);
    assert.ok(gridHeight <= 296);
}
// Preserve a square marker when the native page density makes a grid cell narrower.
const squareMarker = `Modifier.widthIn(max = 29.5.dp).aspectRatio(1f)
                                    .border(3.dp, Color.White, RoundedCornerShape(0.dp))`;
assert.ok(picker.includes(squareMarker), 'Grid selection must keep equal width and height within its cell');
picker = picker.replace(squareMarker, 'Modifier.size(29.5.dp).border(3.dp, Color.White, RoundedCornerShape(0.dp))');
picker = picker.replace('import androidx.compose.foundation.layout.aspectRatio\n', '')
    .replace('import androidx.compose.foundation.layout.widthIn\n', '');
// The page indicator extends the preset area by 12dp below the unchanged swatch grid.
picker = picker.replace('Box(Modifier.fillMaxWidth().height(107.dp))', 'Box(Modifier.fillMaxWidth().height(95.dp))');
// Preset editing is a user-requested behavior extension within the original palette footprint.
const presetCall = `                    PickerPresetColors(
                        selectedColor = selectedColor.copy(alpha = opacity),
                        defaults = swatches,
                        addButtonColor = addButtonColor,
                        onSelected = { editedColor = it.copy(alpha = 1f); onOpacityChange(it.alpha) },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(start = 105.dp).offset(y = 20.dp),
                    )`;
assert.ok(picker.includes(presetCall), 'Preset editor must receive current RGBA and preserve the original palette position');
const presetStart = source.indexOf('                    Column(Modifier.width(249.dp)');
assert.ok(presetStart >= 0, 'Framework preset area missing');
let presetEnd = source.indexOf(') {', presetStart) + 2;
let depth = 0;
do {
    if (source[presetEnd] === '{') depth++;
    else if (source[presetEnd] === '}') depth--;
    presetEnd++;
} while (depth > 0 && presetEnd < source.length);
assert.equal(depth, 0, 'Framework preset area must have a complete body');
const originalPresets = source.slice(presetStart, presetEnd).replace(
    'Column(Modifier.width(249.dp).align(Alignment.TopStart).offset(x = 105.dp, y = 20.dp),',
    'Column(Modifier.fillMaxWidth().align(Alignment.TopStart).padding(start = 105.dp).offset(y = 20.dp),');
picker = picker.replace(presetCall, originalPresets);
const presetEditor = port('PickerPresetColors.kt');
for (const geometry of ['Modifier.height(75.dp)', 'Arrangement.spacedBy(15.dp)', 'repeat(2)', 'repeat(5)',
    'Modifier.size(30.dp)', 'Modifier.size(22.dp)', 'Modifier.size(14.666.dp)', '2.667.dp.toPx()']) {
    assert.ok(presetEditor.includes(geometry), `Preset editor must preserve framework geometry: ${geometry}`);
}
// Outside the editable preset subtree, only Chinese labels and responsive width are adapted.
for (const [chinese, english] of [
    ['"红色"', '"RED"'], ['"绿色"', '"GREEN"'], ['"蓝色"', '"BLUE"'],
    ['"十六进制颜色 #"', '"Display P3 Hex Color #"'], ['"颜色"', '"Colors"'],
    ['"网格", "光谱", "滑块"', '"Grid", "Spectrum", "Sliders"'],
    ['"不透明度", style = PickerOpacityStyle', '"OPACITY", style = PickerOpacityStyle'],
]) {
    assert.ok(picker.includes(chinese), `Missing Chinese picker label: ${chinese}`);
    picker = picker.replace(chinese, english);
}
for (const label of ['取色器', '关闭颜色选择器']) {
    const semantics = `                            .semantics { contentDescription = "${label}" }\n`;
    assert.ok(picker.includes(semantics), `Missing accessible toolbar label: ${label}`);
    picker = picker.replace(semantics, '');
}
const responsivePresets = 'Column(Modifier.fillMaxWidth().align(Alignment.TopStart).padding(start = 105.dp).offset(y = 20.dp),';
assert.ok(picker.includes(responsivePresets), 'Presets must measure remaining width instead of translating a fixed-width column');
picker = picker.replace(responsivePresets, 'Column(Modifier.width(249.dp).align(Alignment.TopStart).offset(x = 105.dp, y = 20.dp),');
// Native appearance uses system density; glass appearance scales the page toward 402dp.
for (const windowWidth of [320, 360, 393, 402, 600]) {
    for (const scale of [1, Math.max(0.85, Math.min(1, windowWidth / 402))]) {
        const contentWidth = windowWidth / scale - 2 * 8 - 2 * 16;
        const presetWidth = contentWidth - 105;
        assert.ok(presetWidth >= 5 * 30, `Five original swatches must fit at ${windowWidth}dp / scale ${scale}`);
        assert.ok(105 + presetWidth <= contentWidth, 'Last swatch and add button must stay inside the content');
    }
}
assert.equal(402 - 2 * 8 - 2 * 16 - 105, 249, 'Preserve original preset geometry at framework reference width');
picker = picker.replace('\ninternal fun colorPickerGridIndex(color: Color): Int = PickerGridColors.indexOf(color.copy(alpha = 1f))\n', '');
picker = picker.replace('    initialColor: Color? = null,\n    onColorChange: (Color) -> Unit = {},\n    panelModifier: Modifier = Modifier,\n', '');
picker = picker.replace('mutableStateOf(initialColor?.copy(alpha = 1f))', 'mutableStateOf<Color?>(null)');
picker = picker.replace('    // Business output only; the framework\'s visual tree remains unchanged.\n    val output = rememberUpdatedState(onColorChange)\n    androidx.compose.runtime.LaunchedEffect(selectedColor, opacity) {\n        output.value(selectedColor.copy(alpha = opacity))\n    }\n', '');
assert.ok(picker.includes('scrimColor = Color.Transparent'), 'Modal backdrop must remain undimmed');
picker = picker.replace('    Ios27BottomSheetDialog(\n',
    '    val dimColor = Color(0xFF29293A).copy(alpha = if (dark) 0.52f else 0.23f)\n\n    Ios27BottomSheetDialog(\n');
picker = picker.replace('scrimColor = Color.Transparent', 'scrimColor = dimColor');
picker = picker.replace('        panelModifier = panelModifier,\n', '');
assert.equal(picker, source, 'Picker must match framework except state/output/animation hooks, Chinese labels and responsive preset width');
let sheet = port('FrameworkColorPickerSheet.kt');
sheet = sheet.replace('    panelModifier: Modifier = Modifier,\n', '');
assert.ok(!sheet.includes('NativeColorPickerSheet'), 'Color picker must stay on the framework sheet');
sheet = sheet.replace('    val glass = LocalSettingsAppearance.current.liquidGlass\n', '');
sheet = sheet.replace(`    val surface = if (glass) {
        if (dark) Color(0xCC2C2C2E) else Color.White.copy(alpha = 0.70f)
    } else if (dark) Color(0xFF2C2C2E) else Color.White`,
    '    val surface = if (dark) Color(0xCC2C2C2E) else Color.White.copy(alpha = 0.70f)');
assert.ok(sheet.includes('backdrop = if (glass) backdrop else null'), 'Disabled glass must use the framework solid fallback');
sheet = sheet.replace('backdrop = if (glass) backdrop else null', 'backdrop = backdrop');
sheet = sheet.replace('panelModifier = panelModifier.ios27AndroidPane', 'panelModifier = Modifier.ios27AndroidPane');
assert.equal(sheet, original('Ios27ColorPickerSheetSurface.kt'), 'Sheet geometry/material must match framework');
for (const [local, upstream] of [
    ['FrameworkSceneGlyphs.kt', 'SceneGlyphs.kt'],
    ['FrameworkColorPickerBehavior.kt', 'AndroidColorPickerBehavior.kt'],
    ['FrameworkBottomCorners.kt', 'PredictiveBackSupport.kt'],
]) assert.equal(port(local), original(upstream), `${upstream} must remain unchanged`);
const asset = 'src/main/assets/iphone-examples/12740-35184.json';
assert.deepEqual(readFileSync(resolve(root, 'app', asset)), readFileSync(resolve(framework, 'sample', asset)),
    'Original toolbar glyph asset must be byte-identical');
console.log('Framework picker checks passed: original shell with glass/solid material, preset footprint and source assets.');
