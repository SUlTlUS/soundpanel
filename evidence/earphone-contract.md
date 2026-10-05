# Native earphone control contract

Recovered from this phone's SystemUI.apk and /product/priv-app/MyDevices/MyDevices.apk.

## AirPods (MyDevices)

- AirpodsBluetoothGateway: android.bluetooth.OplusBluetoothDevice(BluetoothDevice), checkIsAirpodsDevice().
- AirpodsBluetoothManager.G(): getExtendFeatureStatus(4) returns 1=off, 2=ANC, 3=transparency, 4=adaptive.
- AirpodsSettingActivity.onNoiseControlClick(): setExtendFeatureStatus(4, mode).
- AirpodsUtils (aa.b2).J() / FeatureSupportInfo: mask 4=ANC, 8=adaptive, 512=off, 1024=transparency.
- AirpodsSettingActivity.getNoiseSelectorItems(): ANC, adaptive, off, transparency, filtered by capability.
- NoiseReductionCommand: feature 2048 is wear status; status 3 or one-ear status 1/2 with feature 8192=0 blocks ANC/adaptive.
- SystemUI drawable names: qs_detail_tile_noise_reduction_open, qs_detail_tile_noise_reduction_adaptive,
  qs_detail_tile_noise_reduction_transparent; off uses qs_detail_tile_earphne.

## Other system-supported earphones (Melody)

- EarphoneController queries content://com.oplus.melody.provider.EarphoneControlProvider/melody_method_active_device.
- It queries /melody_method_noise_reduction with selection="address" and one address argument.
- Cursor columns: name, address, type, supports (JSON integer list).
- call(base URI, "melody_method_noise_reduction", null, {name, address, type}) requests a change.
- NoiseReductionDetailTile: 1=off, 5=ANC, 10=adaptive, 2=transparency, ordered [1,5,10,2] and filtered by supports.

Module reads both sources asynchronously while the stock volume rail is visible. It never infers
support from a Bluetooth device name. An accepted request gets immediate pending feedback; stable
readback confirms it. Rapid taps advance from the last requested mode, while old cache reports are
ignored during the acknowledgement window. After timeout the UI follows the real readback again.
Icons use the stock neutral tint, including active modes.
No Bluetooth raw packets, imported vendor UI, or new overlay windows are used.
