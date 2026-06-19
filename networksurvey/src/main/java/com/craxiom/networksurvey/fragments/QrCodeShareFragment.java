package com.craxiom.networksurvey.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import timber.log.Timber;

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

        SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        MqttConnectionSettings mqttConnectionSettings = viewModel.getMqttConnectionSettings();
        if (mqttConnectionSettings == null) return view;

        // Removing the device name because if two devices use the same MQTT client ID, then neither will be able to connect
        String mqttConnectionSettingsJson = new Gson().toJson(mqttConnectionSettings.withoutDeviceName());
        if (mqttConnectionSettingsJson == null) return view;

        Bitmap bitmap = generateQrCodeBitmap(mqttConnectionSettingsJson);

        if (bitmap != null)
        {
            imageView.setImageBitmap(bitmap);
        } else
        {
            // QR code generation failed - show error and navigate back
            showQrCodeErrorDialog(viewModel, mqttConnectionSettings);
        }

        return view;
    }

    /**
     * Generates a QR code bitmap from the provided text.
     *
     * @param text The text to encode in the QR code
     * @return The generated Bitmap, or null if generation failed
     */
    private Bitmap generateQrCodeBitmap(String text)
    {
        try
        {
            return textToImage(text, 500, 500);
        } catch (WriterException e)
        {
            Timber.w(e, "Failed to generate QR code - data may be too large. Data size: %d bytes", text.length());
            return null;
        }
    }

    /**
     * Shows an error dialog when QR code generation fails and navigates back to the MQTT
     * connection screen when the user dismisses the dialog.
     *
     * @param viewModel              The SharedViewModel for navigation
     * @param mqttConnectionSettings The current MQTT settings to preserve
     */
    private void showQrCodeErrorDialog(SharedViewModel viewModel, MqttConnectionSettings mqttConnectionSettings)
    {
        FragmentActivity activity = getActivity();
        if (activity == null) return;

        FragmentDialogs.showQrCodeTooLargeError(getParentFragmentManager(),
                () -> viewModel.triggerNavigationToMqttConnection(mqttConnectionSettings));
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