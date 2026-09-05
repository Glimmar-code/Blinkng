package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.R

private val interProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val inter = GoogleFont("Inter")
private val poppins = GoogleFont("Poppins")

val InterFontFamily = FontFamily(
    Font(googleFont = inter, fontProvider = interProvider, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = interProvider, weight = FontWeight.Medium),
    Font(googleFont = inter, fontProvider = interProvider, weight = FontWeight.SemiBold),
    Font(googleFont = inter, fontProvider = interProvider, weight = FontWeight.Bold)
)

val PoppinsFontFamily = FontFamily(
    Font(googleFont = poppins, fontProvider = interProvider, weight = FontWeight.Normal),
    Font(googleFont = poppins, fontProvider = interProvider, weight = FontWeight.Medium),
    Font(googleFont = poppins, fontProvider = interProvider, weight = FontWeight.SemiBold),
    Font(googleFont = poppins, fontProvider = interProvider, weight = FontWeight.Bold)
)

private val defaults = Typography()

val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = InterFontFamily),
    displayMedium = defaults.displayMedium.copy(fontFamily = InterFontFamily),
    displaySmall = defaults.displaySmall.copy(fontFamily = InterFontFamily),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = InterFontFamily),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = InterFontFamily),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = defaults.titleLarge.copy(fontFamily = InterFontFamily),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = defaults.titleSmall.copy(fontFamily = InterFontFamily),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = defaults.bodySmall.copy(fontFamily = InterFontFamily),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)

/** Rounded typography used only by the reference-inspired message surfaces. */
val PoppinsTypography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = PoppinsFontFamily),
    displayMedium = defaults.displayMedium.copy(fontFamily = PoppinsFontFamily),
    displaySmall = defaults.displaySmall.copy(fontFamily = PoppinsFontFamily),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = PoppinsFontFamily),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = PoppinsFontFamily),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = PoppinsFontFamily),
    titleLarge = defaults.titleLarge.copy(fontFamily = PoppinsFontFamily),
    titleMedium = defaults.titleMedium.copy(fontFamily = PoppinsFontFamily),
    titleSmall = defaults.titleSmall.copy(fontFamily = PoppinsFontFamily),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = PoppinsFontFamily),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = PoppinsFontFamily),
    bodySmall = defaults.bodySmall.copy(fontFamily = PoppinsFontFamily),
    labelLarge = defaults.labelLarge.copy(fontFamily = PoppinsFontFamily),
    labelMedium = defaults.labelMedium.copy(fontFamily = PoppinsFontFamily),
    labelSmall = defaults.labelSmall.copy(fontFamily = PoppinsFontFamily)
)
