# CipherFusion Modular Upgrade

This build keeps the existing Text Encryption `MainActivity` and its Vigenere-style byte + 16x16 Polybius engine intact. A new login gate launches a dashboard, which opens separate File and Media modules.

## Demo credentials
- admin / admin123
- user / user123
- demo / demo123

These credentials are intentionally fixed for a project/demo build and are NOT suitable for production authentication.

## Modules
1. Text Encryption — existing engine, isolated in MainActivity.
2. File Encryption — `.cffile` container with streaming byte processing.
3. Media Encryption — Photo / Video / Audio tabs using the same isolated file container engine.

## Important
The custom Vigenere + Polybius design is educational/custom cryptography, not modern authenticated encryption. For real-world security, add an AES-256-GCM mode later.
