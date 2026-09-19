package com.example.data.models

import com.blinkng.shared.BlinkOnboardingPolicy

/**
 * Controlled onboarding options used by BLINK account setup.
 *
 * Universities come from [NigerianUniversities]. Departments are intentionally broad
 * so users can search instead of scrolling through a fixed faculty-specific list.
 */
object BlinkOnboardingCatalog {
    val departments: List<String> = listOf(
        "Accounting",
        "Actuarial Science",
        "Agricultural Economics",
        "Agricultural Engineering",
        "Agricultural Extension",
        "Agriculture",
        "Anatomy",
        "Animal Science",
        "Architecture",
        "Biochemistry",
        "Biology",
        "Biomedical Engineering",
        "Biotechnology",
        "Building",
        "Business Administration",
        "Chemical Engineering",
        "Chemistry",
        "Civil Engineering",
        "Computer Engineering",
        "Computer Science",
        "Cyber Security",
        "Dentistry",
        "Economics",
        "Education",
        "Electrical and Electronics Engineering",
        "English",
        "Entrepreneurship",
        "Estate Management",
        "Finance",
        "Fisheries and Aquaculture",
        "Food Science and Technology",
        "Forestry",
        "Geography",
        "Geology",
        "Geomatics",
        "Human Anatomy",
        "Human Physiology",
        "Industrial Chemistry",
        "Industrial Design",
        "Industrial Mathematics",
        "Industrial Physics",
        "Information Systems",
        "Information Technology",
        "Law",
        "Library and Information Science",
        "Marketing",
        "Mass Communication",
        "Mathematics",
        "Mechanical Engineering",
        "Mechatronics Engineering",
        "Medical Laboratory Science",
        "Medicine and Surgery",
        "Meteorology",
        "Microbiology",
        "Mining Engineering",
        "Nursing",
        "Nutrition and Dietetics",
        "Petroleum Engineering",
        "Pharmacy",
        "Physics",
        "Physiology",
        "Physiotherapy",
        "Political Science",
        "Project Management Technology",
        "Psychology",
        "Quantity Surveying",
        "Remote Sensing and GIS",
        "Science Laboratory Technology",
        "Sociology",
        "Software Engineering",
        "Statistics",
        "Surveying and Geoinformatics",
        "Telecommunication Engineering",
        "Transport Management",
        "Urban and Regional Planning"
    ).distinct().sorted()

    val levels: List<String> = BlinkOnboardingPolicy.levels
    val genders: List<String> = BlinkOnboardingPolicy.genders
    val interestGroups: Map<String, List<String>> = BlinkOnboardingPolicy.interestGroups
    val allInterests: List<String> = BlinkOnboardingPolicy.allInterests

}
