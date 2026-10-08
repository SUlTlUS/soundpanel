#!/system/bin/sh
set -eu

ACE_HASH='29ff19621cb37017cfd195109e2b4ed202175c72c1a0d247519ec866d1dabcbe'
OP15_HASH='31a3d5ad620488c64a77ac8a08b051686b2e177d84445c614cb904a129391a77'
SYSTEM_APK='/system_ext/priv-app/SystemUI/SystemUI.apk'
BACKUP_ROOT='/data/local/tmp/systemui-switcher-backups'
ACTION="$1"
test "$(id -u)" = 0

digest() { sha256sum "$1" | cut -d ' ' -f 1; }
current_apk() { pm path com.android.systemui | sed -n 's/^package://p' | head -n 1; }
guard_system() {
    test "$(getprop ro.build.version.sdk)" = 37
    test "$(digest "$SYSTEM_APK")" = "$ACE_HASH"
}
guard_update() {
    case "$1" in /data/app/*/com.android.systemui-*/base.apk) ;; *) exit 23 ;; esac
    test "$(readlink -f "${1%/base.apk}")" = "${1%/base.apk}"
    test "$(digest "$1")" = "$OP15_HASH"
}
guard_backup() {
    case "$1" in "$BACKUP_ROOT"/restore-*) ;; *) exit 24 ;; esac
    test "${1%/*}" = "$BACKUP_ROOT"
    test ! -L "$BACKUP_ROOT"
}

case "$ACTION" in
    inspect)
        echo "ROOT_UID=$(id -u)"
        echo "MODEL=$(getprop ro.product.model)"
        echo "ROM=$(getprop ro.build.display.id)"
        echo "SDK=$(getprop ro.build.version.sdk)"
        echo "BOOT=$(cat /proc/sys/kernel/random/boot_id)"
        APK="$(current_apk)"
        echo "CURRENT_PATH=$APK"
        if test -f "$APK"; then echo "CURRENT_HASH=$(digest "$APK")"; else echo 'CURRENT_HASH='; fi
        if test -f "$SYSTEM_APK"; then echo "SYSTEM_HASH=$(digest "$SYSTEM_APK")"; else echo 'SYSTEM_HASH='; fi
        PIDS="$(pidof com.android.systemui || true)"
        MATCH=false
        if test -n "$APK"; then
            for PID in $PIDS; do
                if grep -F "$APK" "/proc/$PID/maps" >/dev/null 2>&1; then MATCH=true; fi
            done
        fi
        echo "RUNNING_MATCH=$MATCH"
        cmd package list staged-sessions || true
        ;;
    install)
        guard_system
        APK="$2"
        test "$(digest "$APK")" = "$OP15_HASH"
        CURRENT="$(current_apk)"
        if test "$CURRENT" = "$SYSTEM_APK"; then
            test "$(digest "$CURRENT")" = "$ACE_HASH"
        else
            case "$CURRENT" in /data/app/*/com.android.systemui-*/base.apk) ;; *) exit 23 ;; esac
            test "$(readlink -f "${CURRENT%/base.apk}")" = "${CURRENT%/base.apk}"
            HASH="$(digest "$CURRENT")"
            test "$HASH" = "$ACE_HASH" || test "$HASH" = "$OP15_HASH"
        fi
        pm install --staged -r --staged-ready-timeout 10000 "$APK"
        ;;
    restore)
        guard_system
        APK="$(current_apk)"
        test "$APK" = "$3"
        guard_update "$APK"
        BACKUP="$2"
        guard_backup "$BACKUP"
        test ! -e "$BACKUP"
        mkdir -p "$BACKUP_ROOT"
        test "$(readlink -f "$BACKUP_ROOT")" = "$BACKUP_ROOT"
        chmod 700 "$BACKUP_ROOT"
        mkdir "$BACKUP"
        chmod 700 "$BACKUP"
        cp -p /data/system/packages.xml "$BACKUP/packages.xml.before"
        if test -f /data/system/packages.list; then cp -p /data/system/packages.list "$BACKUP/packages.list.before"; fi
        printf '%s\n' "${APK%/base.apk}" > "$BACKUP/original-path.txt"
        mv "${APK%/base.apk}" "$BACKUP/update"
        test "$(digest "$BACKUP/update/base.apk")" = "$OP15_HASH"
        test ! -e "${APK%/base.apk}"
        sync
        echo "RESTORE_PREPARED=$BACKUP"
        ;;
    undo-restore)
        guard_system
        BACKUP="$2"
        ORIGINAL="$3"
        test "$(cat /proc/sys/kernel/random/boot_id)" = "$4"
        guard_backup "$BACKUP"
        case "$ORIGINAL" in /data/app/*/com.android.systemui-*) ;; *) exit 25 ;; esac
        test "$(cat "$BACKUP/original-path.txt")" = "$ORIGINAL"
        test ! -e "$ORIGINAL"
        test "$(digest "$BACKUP/update/base.apk")" = "$OP15_HASH"
        test "$(readlink -f "$BACKUP/update")" = "$BACKUP/update"
        mv "$BACKUP/update" "$ORIGINAL"
        sync
        echo 'RESTORE_CANCELLED'
        ;;
    cancel)
        guard_system
        for SESSION in $(printf '%s' "$2" | tr ',' ' '); do
            case "$SESSION" in ''|*[!0-9]*) exit 26 ;; esac
            cmd package install-abandon "$SESSION"
        done
        ;;
    reboot)
        guard_system
        sync
        reboot
        ;;
    *) exit 27 ;;
esac
