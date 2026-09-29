package com.cuenti.app.views.components;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.vaadin.flow.component.html.Image;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** A QR code rendered server-side as an inline SVG image; nothing leaves the server. */
public final class QrCode {

    private QrCode() {}

    public static Image image(String content, String alt) {
        Image image = new Image("data:image/svg+xml;base64," + Base64.getEncoder()
                .encodeToString(svg(content).getBytes(StandardCharsets.UTF_8)), alt);
        image.setWidth("200px");
        image.setHeight("200px");
        // dark modules on white in either theme so every scanner reads it
        image.getStyle().set("background", "white").set("padding", "8px").set("border-radius", "8px");
        return image;
    }

    static String svg(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + matrix.getWidth() + " "
                    + matrix.getHeight() + "\" shape-rendering=\"crispEdges\"><path fill=\"#000\" d=\""
                    + path + "\"/></svg>";
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        }
    }
}
