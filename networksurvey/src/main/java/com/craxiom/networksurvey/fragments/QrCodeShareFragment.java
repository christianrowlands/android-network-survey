package com.craxiom.networksurvey.fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;
import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/**
 * Fragment responsible for sharing the MQTT connection settings via a QR Code.
 */
public class QrCodeShareFragment extends Fragment
{

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_qr_code_share, container, false);

        ImageView imageView = view.findViewById(R.id.ivQrCode);

        MqttConnectionSettings mqttConnectionSettings =
                QrCodeShareFragmentArgs.fromBundle(getArguments()).getMqttConnectionSettings();
        if (mqttConnectionSettings == null) return view;

        // Removing the device name because if two devices use the same MQTT client ID, then neither will be able to connect
        String mqttConnectionSettingsJson = new Gson().toJson(mqttConnectionSettings.withoutDeviceName());
        if (mqttConnectionSettingsJson == null) return view;

        Bitmap bitmap;
        try
        {
            bitmap = textToImage(mqttConnectionSettingsJson, 500, 500);
        } catch (WriterException e)
        {
            throw new RuntimeException(e);
        }

        if (bitmap != null)
        {
            imageView.setImageBitmap(bitmap);
        }

        return view;
    }

    /**
     * Given the provided text, convert it to a Bitmap that represents a QR Code.
     */
    private Bitmap textToImage(String text, int width, int height) throws WriterException, NullPointerException
    {
        BitMatrix bitMatrix;
        try
        {
            bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE,
                    width, height, null);
        } catch (IllegalArgumentException Illegalargumentexception)
        {
            return null;
        }

        int bitMatrixWidth = bitMatrix.getWidth();
        int bitMatrixHeight = bitMatrix.getHeight();
        int[] pixels = new int[bitMatrixWidth * bitMatrixHeight];

        int colorWhite = 0xFFFFFFFF;
        int colorBlack = 0xFF000000;

        for (int y = 0; y < bitMatrixHeight; y++)
        {
            int offset = y * bitMatrixWidth;
            for (int x = 0; x < bitMatrixWidth; x++)
            {
                pixels[offset + x] = bitMatrix.get(x, y) ? colorBlack : colorWhite;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(bitMatrixWidth, bitMatrixHeight, Bitmap.Config.ARGB_4444);

        bitmap.setPixels(pixels, 0, width, 0, 0, bitMatrixWidth, bitMatrixHeight);
        return bitmap;
    }
}