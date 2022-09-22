# nidoca-iptv
iptv player for android

# current apk download

## debug
[(Link)https://koshisinthehouse.github.io/nidoca-iptv/debug/app-debug.apk]

## release
[(Link)https://koshisinthehouse.github.io/nidoca-iptv/release/app-release.apk]

# set keystore vars in Windows
    [System.Environment]::SetEnvironmentVariable("RELEASE_KEY_ALIAS", "<value>", "User")
    [System.Environment]::SetEnvironmentVariable("RELEASE_STORE_PASSWORD", "<value>", "User")
    [System.Environment]::SetEnvironmentVariable("RELEASE_KEY_PASSWORD", "<value>", "User")


./gradlew :app:bundleDebug
./gradlew :app:bundleRelease
