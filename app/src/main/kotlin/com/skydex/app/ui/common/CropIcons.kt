package com.skydex.app.ui.common

import androidx.annotation.DrawableRes
import com.skydex.app.R
import com.skydex.shared.model.Crop

// Crop icons are SkyCrypt's item renders (sky.shiiyu.moe/api/item/<ID>), downloaded once and bundled in
// res/drawable-nodpi, like the skill and event icons: the app never fetches them at runtime. Carrot and potato are
// vanilla Minecraft item textures instead, since SkyCrypt's planted-crop renders of the two look alike.

/** Bundled icon for a Jacob's contest crop. */
@DrawableRes
fun cropIcon(crop: Crop): Int = when (crop) {
    Crop.CACTUS -> R.drawable.crop_cactus
    Crop.CARROT -> R.drawable.crop_carrot
    Crop.COCOA_BEANS -> R.drawable.crop_cocoa_beans
    Crop.MELON -> R.drawable.crop_melon
    Crop.MOONFLOWER -> R.drawable.crop_moonflower
    Crop.MUSHROOM -> R.drawable.crop_mushroom
    Crop.NETHER_WART -> R.drawable.crop_nether_wart
    Crop.POTATO -> R.drawable.crop_potato
    Crop.PUMPKIN -> R.drawable.crop_pumpkin
    Crop.SUGAR_CANE -> R.drawable.crop_sugar_cane
    Crop.SUNFLOWER -> R.drawable.crop_sunflower
    Crop.WHEAT -> R.drawable.crop_wheat
    Crop.WILD_ROSE -> R.drawable.crop_wild_rose
}

/** e.g. "Crops: Wheat, Cactus, Melon" (TalkBack label for a contest's crop icons). */
fun cropsLabel(crops: List<Crop>): String = "Crops: " + crops.joinToString { it.displayName }
