package org.boringreport.boringlauncher

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

class FontManager {
    companion object {
        private val provider = GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs
        )

        private val fontName = GoogleFont("Poppins")

        val fontFamily = FontFamily(
            Font(
                googleFont = fontName,
                fontProvider = provider,
            )
        )
    }
}