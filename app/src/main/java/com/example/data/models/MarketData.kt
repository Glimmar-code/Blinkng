package com.example.data.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class MarketCategoryItem(
    val name: String,
    val icon: ImageVector
)

val kMarketCategoriesList: List<MarketCategoryItem> = listOf(
    MarketCategoryItem("All Categories", Icons.Default.Category),
    MarketCategoryItem("Phones & Tablets", Icons.Default.Smartphone),
    MarketCategoryItem("Laptops & Computers", Icons.Default.Laptop),
    MarketCategoryItem("Computer Accessories", Icons.Default.Mouse),
    MarketCategoryItem("Electronics", Icons.Default.Devices),
    MarketCategoryItem("Audio & Headphones", Icons.Default.Headphones),
    MarketCategoryItem("Cameras & Photography", Icons.Default.CameraAlt),
    MarketCategoryItem("Gaming & Consoles", Icons.Default.SportsEsports),
    MarketCategoryItem("Power Banks & Chargers", Icons.Default.BatteryChargingFull),
    MarketCategoryItem("Books & Study Materials", Icons.Default.MenuBook),
    MarketCategoryItem("Men's Fashion", Icons.Default.Man),
    MarketCategoryItem("Women's Fashion", Icons.Default.Woman),
    MarketCategoryItem("Shoes & Footwear", Icons.Default.Hiking),
    MarketCategoryItem("Bags & Backpacks", Icons.Default.ShoppingBag),
    MarketCategoryItem("Watches & Jewelry", Icons.Default.WatchLater),
    MarketCategoryItem("Hostel Furniture", Icons.Default.Chair),
    MarketCategoryItem("Kitchen & Appliances", Icons.Default.Kitchen),
    MarketCategoryItem("Food & Groceries", Icons.Default.Restaurant),
    MarketCategoryItem("Campus Freelance Services", Icons.Default.Work),
    MarketCategoryItem("Event & Party Tickets", Icons.Default.ConfirmationNumber)
)

val kNigerianStatesList: List<String> = listOf(
    "Abia", "Adamawa", "Akwa Ibom", "Anambra", "Bauchi", "Bayelsa", "Benue",
    "Borno", "Cross River", "Delta", "Ebonyi", "Edo", "Ekiti", "Enugu",
    "FCT - Abuja", "Gombe", "Imo", "Jigawa", "Kaduna", "Kano", "Katsina",
    "Kebbi", "Kogi", "Kwara", "Lagos", "Nasarawa", "Niger", "Ogun", "Ondo",
    "Osun", "Oyo", "Plateau", "Rivers", "Sokoto", "Taraba", "Yobe", "Zamfara"
)

val kNigerianUniversitiesList: List<String> = listOf(
    "University of Lagos (UNILAG)",
    "University of Ibadan (UI)",
    "Obafemi Awolowo University (OAU)",
    "Ahmadu Bello University (ABU Zaria)",
    "University of Nigeria, Nsukka (UNN)",
    "Covenant University (CU)",
    "Federal University of Technology, Akure (FUTA)",
    "Federal University of Technology, Minna (FUTMinna)",
    "University of Benin (UNIBEN)",
    "University of Ilorin (UNILORIN)",
    "Lagos State University (LASU)",
    "Babcock University",
    "Landmark University",
    "Pan-Atlantic University",
    "Nnamdi Azikiwe University (UNIZIK)",
    "Rivers State University",
    "University of Port Harcourt (UNIPORT)"
)
