package com.example.camerax_qr_deeplink;

public interface QRCodeFoundListener {

    void onQRCodeFound(String qrCode);
    void onQRCodeNotFound();
}
