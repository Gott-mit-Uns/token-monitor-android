#!/usr/bin/env python3
"""Build a stable personal release; signing password stays in macOS Keychain and process memory."""
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SWIFT = r'''
import Foundation
import Security
let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "io.github.theminionooo.tokenmonitor.cloudflare.signing", kSecAttrAccount as String: "release"]
if CommandLine.arguments.last == "get" {
    var query = base; query[kSecReturnData as String] = true; query[kSecMatchLimit as String] = kSecMatchLimitOne
    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)
    if status == errSecItemNotFound { exit(2) }
    guard status == errSecSuccess, let data = item as? Data else { exit(3) }
    FileHandle.standardOutput.write(data)
} else {
    guard let value = ProcessInfo.processInfo.environment["TM_PRIVATE_SIGNING_PASSWORD"] else { exit(4) }
    var query = base; query[kSecValueData as String] = Data(value.utf8)
    guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else { exit(5) }
}
'''

def main():
    if sys.platform != "darwin":
        raise SystemExit("Use documented signing environment variables on this platform.")
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        raise SystemExit("Set JAVA_HOME to a JDK 17 installation.")
    private = Path.home() / ".local/share/token-monitor-minion-cloudflare/private"
    private.mkdir(parents=True, exist_ok=True); private.chmod(0o700)
    keystore = private / "release.jks"
    with tempfile.TemporaryDirectory(prefix="tm-signing-") as tmp:
        swift = Path(tmp) / "keychain.swift"; swift.write_text(SWIFT)
        result = subprocess.run(["/usr/bin/swift", str(swift), "get"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode == 2:
            if keystore.exists():
                raise SystemExit("Existing signing key has no Keychain password; restore it before signing.")
            password = secrets.token_urlsafe(36)
            env = dict(os.environ, TM_PRIVATE_SIGNING_PASSWORD=password)
            subprocess.run(["/usr/bin/swift", str(swift), "put"], env=env, check=True, stdout=subprocess.DEVNULL)
        elif result.returncode == 0:
            password = result.stdout.decode()
        else:
            raise SystemExit("Keychain access failed; no signing material was printed.")
    env = dict(os.environ, ANDROID_KEYSTORE_FILE=str(keystore), ANDROID_KEYSTORE_PASSWORD=password,
               ANDROID_KEY_PASSWORD=password, ANDROID_KEY_ALIAS="token-monitor-release")
    if not keystore.exists():
        subprocess.run([str(Path(java_home)/"bin/keytool"), "-genkeypair", "-keystore", str(keystore),
                        "-storetype", "PKCS12", "-storepass:env", "ANDROID_KEYSTORE_PASSWORD",
                        "-keypass:env", "ANDROID_KEY_PASSWORD", "-alias", env["ANDROID_KEY_ALIAS"],
                        "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
                        "-dname", "CN=Token Monitor Minion CF Personal Release", "-noprompt"],
                       env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        keystore.chmod(0o600)
    gradle = os.environ.get("TM_GRADLE_EXECUTABLE", str(ROOT/"gradlew"))
    subprocess.run([gradle, ":app:assembleRelease", "--console=plain"], cwd=ROOT, env=env, check=True)

if __name__ == "__main__":
    main()
