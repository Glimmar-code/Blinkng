-- Feature 8: shared Nigerian university directory. Reviewed against the
-- National Universities Commission's Nigerian University System on 2026-10-18.
-- Legacy entries are explicitly labelled pending reconciliation; no entry
-- is a guarantee of present-day programme accreditation or student identity.
-- The catalog is additive, supports historical names, and never auto-publishes
-- a user's missing-university request.
begin;

create table if not exists public.blink_universities (
  id text primary key,
  official_name text not null,
  ownership text not null check (ownership in ('FEDERAL','STATE','PRIVATE')),
  state text not null default '',
  source_label text not null check (source_label in ('legacy_review','nuc_2026_review')),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists idx_blink_universities_name
  on public.blink_universities (lower(trim(official_name)));

insert into public.blink_universities(id, official_name, ownership, state, source_label)
values
  ('ng-university:abubakar-tafawa-balewa-university-bauchi', 'Abubakar Tafawa Balewa University, Bauchi', 'FEDERAL', 'Bauchi', 'legacy_review'),
  ('ng-university:ahmadu-bello-university-zaria', 'Ahmadu Bello University, Zaria', 'FEDERAL', 'Kaduna', 'legacy_review'),
  ('ng-university:bayero-university-kano', 'Bayero University, Kano', 'FEDERAL', 'Kano', 'legacy_review'),
  ('ng-university:federal-university-of-agriculture-abeokuta', 'Federal University of Agriculture, Abeokuta', 'FEDERAL', 'Ogun', 'legacy_review'),
  ('ng-university:federal-university-of-agriculture-makurdi', 'Federal University of Agriculture, Makurdi', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-birnin-kebbi', 'Federal University Birnin Kebbi', 'FEDERAL', 'Kebbi', 'legacy_review'),
  ('ng-university:federal-university-dutse', 'Federal University Dutse', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-dutsin-ma', 'Federal University Dutsin-Ma', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-gashua', 'Federal University Gashua', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-gusau', 'Federal University Gusau', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-kashere', 'Federal University Kashere', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-lafia', 'Federal University Lafia', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-lokoja', 'Federal University Lokoja', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-otuoke', 'Federal University Otuoke', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-oye-ekiti', 'Federal University Oye-Ekiti', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-wukari', 'Federal University Wukari', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-petroleum-resources-effurun', 'Federal University of Petroleum Resources, Effurun', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-technology-akure', 'Federal University of Technology, Akure', 'FEDERAL', 'Ondo', 'legacy_review'),
  ('ng-university:federal-university-of-technology-babura', 'Federal University of Technology, Babura', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-technology-minna', 'Federal University of Technology, Minna', 'FEDERAL', 'Niger', 'legacy_review'),
  ('ng-university:federal-university-of-technology-owerri', 'Federal University of Technology, Owerri', 'FEDERAL', 'Imo', 'legacy_review'),
  ('ng-university:federal-university-of-transportation-daura', 'Federal University of Transportation, Daura', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-health-sciences-azare', 'Federal University of Health Sciences, Azare', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-health-sciences-ila-orangun', 'Federal University of Health Sciences, Ila Orangun', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-health-sciences-katsina', 'Federal University of Health Sciences, Katsina', 'FEDERAL', 'Katsina', 'legacy_review'),
  ('ng-university:federal-university-of-health-sciences-otukpo', 'Federal University of Health Sciences, Otukpo', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-medical-sciences-jabi', 'Federal University of Medical Sciences, Jabi', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-education-abeokuta', 'Federal University of Education, Abeokuta', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-education-gombe', 'Federal University of Education, Gombe', 'FEDERAL', 'Gombe', 'legacy_review'),
  ('ng-university:federal-university-of-education-kano', 'Federal University of Education, Kano', 'FEDERAL', 'Kano', 'legacy_review'),
  ('ng-university:federal-university-of-education-katsina', 'Federal University of Education, Katsina', 'FEDERAL', 'Katsina', 'legacy_review'),
  ('ng-university:federal-university-of-education-kontagora', 'Federal University of Education, Kontagora', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-education-ondo', 'Federal University of Education, Ondo', 'FEDERAL', 'Ondo', 'legacy_review'),
  ('ng-university:federal-university-of-education-pankshin', 'Federal University of Education, Pankshin', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-education-umunze', 'Federal University of Education, Umunze', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:federal-university-of-education-zaria', 'Federal University of Education, Zaria', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:joseph-sarwuan-tarka-university-makurdi', 'Joseph Sarwuan Tarka University, Makurdi', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:michael-okpara-university-of-agriculture-umudike', 'Michael Okpara University of Agriculture, Umudike', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:modibbo-adama-university-yola', 'Modibbo Adama University, Yola', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:national-open-university-of-nigeria', 'National Open University of Nigeria', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:nigerian-defence-academy-kaduna', 'Nigerian Defence Academy, Kaduna', 'FEDERAL', 'Kaduna', 'legacy_review'),
  ('ng-university:nigerian-maritime-university-okerenkoko', 'Nigerian Maritime University, Okerenkoko', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:nigeria-police-academy-wudil', 'Nigeria Police Academy, Wudil', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:nnamdi-azikiwe-university-awka', 'Nnamdi Azikiwe University, Awka', 'FEDERAL', 'Anambra', 'legacy_review'),
  ('ng-university:obafemi-awolowo-university-ile-ife', 'Obafemi Awolowo University, Ile-Ife', 'FEDERAL', 'Osun', 'legacy_review'),
  ('ng-university:university-of-abuja', 'University of Abuja', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:university-of-benin', 'University of Benin', 'FEDERAL', 'Edo', 'legacy_review'),
  ('ng-university:university-of-calabar', 'University of Calabar', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:university-of-ibadan', 'University of Ibadan', 'FEDERAL', 'Oyo', 'legacy_review'),
  ('ng-university:university-of-ilorin', 'University of Ilorin', 'FEDERAL', 'Kwara', 'legacy_review'),
  ('ng-university:university-of-jos', 'University of Jos', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:university-of-lagos', 'University of Lagos', 'FEDERAL', 'Lagos', 'legacy_review'),
  ('ng-university:university-of-maiduguri', 'University of Maiduguri', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:university-of-nigeria-nsukka', 'University of Nigeria, Nsukka', 'FEDERAL', 'Enugu', 'legacy_review'),
  ('ng-university:university-of-port-harcourt', 'University of Port Harcourt', 'FEDERAL', 'Rivers', 'legacy_review'),
  ('ng-university:university-of-uyo', 'University of Uyo', 'FEDERAL', '', 'legacy_review'),
  ('ng-university:usmanu-danfodiyo-university-sokoto', 'Usmanu Danfodiyo University, Sokoto', 'FEDERAL', 'Sokoto', 'legacy_review'),
  ('ng-university:abia-state-university-uturu', 'Abia State University, Uturu', 'STATE', 'Abia', 'legacy_review'),
  ('ng-university:adamawa-state-university-mubi', 'Adamawa State University, Mubi', 'STATE', 'Adamawa', 'legacy_review'),
  ('ng-university:akwa-ibom-state-university', 'Akwa Ibom State University', 'STATE', 'Akwa Ibom', 'legacy_review'),
  ('ng-university:ambrose-alli-university-ekpoma', 'Ambrose Alli University, Ekpoma', 'STATE', '', 'legacy_review'),
  ('ng-university:anambra-state-university', 'Anambra State University', 'STATE', 'Anambra', 'legacy_review'),
  ('ng-university:bauchi-state-university-gadau', 'Bauchi State University, Gadau', 'STATE', 'Bauchi', 'legacy_review'),
  ('ng-university:benue-state-university', 'Benue State University', 'STATE', 'Benue', 'legacy_review'),
  ('ng-university:borno-state-university', 'Borno State University', 'STATE', 'Borno', 'legacy_review'),
  ('ng-university:chukwuemeka-odumegwu-ojukwu-university', 'Chukwuemeka Odumegwu Ojukwu University', 'STATE', '', 'legacy_review'),
  ('ng-university:cross-river-university-of-technology', 'Cross River University of Technology', 'STATE', '', 'legacy_review'),
  ('ng-university:delta-state-university', 'Delta State University', 'STATE', 'Delta', 'legacy_review'),
  ('ng-university:delta-state-university-of-science-and-technology', 'Delta State University of Science and Technology', 'STATE', 'Delta', 'legacy_review'),
  ('ng-university:dennis-osadebay-university', 'Dennis Osadebay University', 'STATE', '', 'legacy_review'),
  ('ng-university:ebonyi-state-university', 'Ebonyi State University', 'STATE', 'Ebonyi', 'legacy_review'),
  ('ng-university:edo-state-university-uzairue', 'Edo State University Uzairue', 'STATE', 'Edo', 'legacy_review'),
  ('ng-university:ekiti-state-university', 'Ekiti State University', 'STATE', 'Ekiti', 'legacy_review'),
  ('ng-university:enugu-state-university-of-science-and-technology', 'Enugu State University of Science and Technology', 'STATE', 'Enugu', 'legacy_review'),
  ('ng-university:gombe-state-university', 'Gombe State University', 'STATE', 'Gombe', 'legacy_review'),
  ('ng-university:ignatius-ajuru-university-of-education', 'Ignatius Ajuru University of Education', 'STATE', '', 'legacy_review'),
  ('ng-university:imo-state-university', 'Imo State University', 'STATE', 'Imo', 'legacy_review'),
  ('ng-university:kaduna-state-university', 'Kaduna State University', 'STATE', 'Kaduna', 'legacy_review'),
  ('ng-university:kano-state-university-of-science-and-technology', 'Kano State University of Science and Technology', 'STATE', 'Kano', 'legacy_review'),
  ('ng-university:kebbi-state-university-of-science-and-technology', 'Kebbi State University of Science and Technology', 'STATE', 'Kebbi', 'legacy_review'),
  ('ng-university:kogi-state-university-anyigba', 'Kogi State University, Anyigba', 'STATE', 'Kogi', 'legacy_review'),
  ('ng-university:kwara-state-university', 'Kwara State University', 'STATE', 'Kwara', 'legacy_review'),
  ('ng-university:ladoke-akintola-university-of-technology', 'Ladoke Akintola University of Technology', 'STATE', '', 'legacy_review'),
  ('ng-university:lagos-state-university', 'Lagos State University', 'STATE', 'Lagos', 'legacy_review'),
  ('ng-university:nasarawa-state-university', 'Nasarawa State University', 'STATE', 'Nasarawa', 'legacy_review'),
  ('ng-university:niger-delta-university', 'Niger Delta University', 'STATE', '', 'legacy_review'),
  ('ng-university:niger-state-university', 'Niger State University', 'STATE', 'Niger', 'legacy_review'),
  ('ng-university:niger-state-university-of-education', 'Niger State University of Education', 'STATE', 'Niger', 'legacy_review'),
  ('ng-university:niger-state-university-of-medical-sciences', 'Niger State University of Medical Sciences', 'STATE', 'Niger', 'legacy_review'),
  ('ng-university:oil-and-gas-university-delta-state', 'Oil and Gas University, Delta State', 'STATE', 'Delta', 'legacy_review'),
  ('ng-university:olabisi-onabanjo-university', 'Olabisi Onabanjo University', 'STATE', '', 'legacy_review'),
  ('ng-university:olusegun-agagu-university-of-science-and-technology', 'Olusegun Agagu University of Science and Technology', 'STATE', '', 'legacy_review'),
  ('ng-university:osun-state-university', 'Osun State University', 'STATE', 'Osun', 'legacy_review'),
  ('ng-university:plateau-state-university', 'Plateau State University', 'STATE', 'Plateau', 'legacy_review'),
  ('ng-university:rivers-state-university', 'Rivers State University', 'STATE', 'Rivers', 'legacy_review'),
  ('ng-university:sokoto-state-university', 'Sokoto State University', 'STATE', 'Sokoto', 'legacy_review'),
  ('ng-university:sule-lamido-university', 'Sule Lamido University', 'STATE', '', 'legacy_review'),
  ('ng-university:taraba-state-university', 'Taraba State University', 'STATE', 'Taraba', 'legacy_review'),
  ('ng-university:university-of-africa-toru-orua', 'University of Africa, Toru-Orua', 'STATE', '', 'legacy_review'),
  ('ng-university:yobe-state-university', 'Yobe State University', 'STATE', 'Yobe', 'legacy_review'),
  ('ng-university:yusuf-maitama-sule-university', 'Yusuf Maitama Sule University', 'STATE', '', 'legacy_review'),
  ('ng-university:zamfara-state-university', 'Zamfara State University', 'STATE', 'Zamfara', 'legacy_review'),
  ('ng-university:afe-babalola-university', 'Afe Babalola University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:achievers-university', 'Achievers University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:adeleke-university', 'Adeleke University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:admiralty-university-of-nigeria', 'Admiralty University of Nigeria', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:african-university-of-science-and-technology', 'African University of Science and Technology', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:ajayi-crowther-university', 'Ajayi Crowther University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:al-hikmah-university', 'Al-Hikmah University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:al-qalam-university', 'Al-Qalam University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:american-university-of-nigeria', 'American University of Nigeria', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:anchor-university', 'Anchor University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:arthur-jarvis-university', 'Arthur Jarvis University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:ave-maria-university', 'Ave Maria University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:babcock-university', 'Babcock University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:baze-university', 'Baze University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:bells-university-of-technology', 'Bells University of Technology', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:benson-idahosa-university', 'Benson Idahosa University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:bingham-university', 'Bingham University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:bowen-university', 'Bowen University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:caleb-university', 'Caleb University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:caritas-university', 'Caritas University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:covenant-university', 'Covenant University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:crawford-university', 'Crawford University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:crescent-university', 'Crescent University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:christopher-university', 'Christopher University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:dominion-university', 'Dominion University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:edwin-clark-university', 'Edwin Clark University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:elizade-university', 'Elizade University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:evangel-university', 'Evangel University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:fountain-university', 'Fountain University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:godfrey-okoye-university', 'Godfrey Okoye University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:gregory-university-uturu', 'Gregory University, Uturu', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:greenfield-university', 'Greenfield University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:hallmark-university', 'Hallmark University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:hezekiah-university', 'Hezekiah University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:igbinedion-university', 'Igbinedion University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:joseph-ayo-babalola-university', 'Joseph Ayo Babalola University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:kings-university', 'Kings University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:kola-daisi-university', 'Kola Daisi University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:landmark-university', 'Landmark University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:lead-city-university', 'Lead City University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:madonna-university', 'Madonna University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:mcpherson-university', 'McPherson University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:mewar-international-university', 'Mewar International University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:miva-open-university', 'Miva Open University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:mountain-top-university', 'Mountain Top University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:nile-university-of-nigeria', 'Nile University of Nigeria', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:novena-university', 'Novena University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:oduduwa-university', 'Oduduwa University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:pamo-university-of-medical-sciences', 'PAMO University of Medical Sciences', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:pan-atlantic-university', 'Pan-Atlantic University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:paul-university', 'Paul University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:precious-cornerstone-university', 'Precious Cornerstone University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:redeemer-s-university', 'Redeemer''s University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:renaissance-university', 'Renaissance University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:rhema-university', 'Rhema University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:salem-university', 'Salem University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:samuel-adegboyega-university', 'Samuel Adegboyega University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:skyline-university-nigeria', 'Skyline University Nigeria', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:southwestern-university', 'Southwestern University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:summit-university', 'Summit University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:tansian-university', 'Tansian University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:thomas-adewumi-university', 'Thomas Adewumi University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:trinity-university', 'Trinity University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:veritas-university', 'Veritas University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:venite-university', 'Venite University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:wellspring-university', 'Wellspring University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:westland-university', 'Westland University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:western-delta-university', 'Western Delta University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:wigwe-university', 'Wigwe University', 'PRIVATE', '', 'legacy_review'),
  ('ng-university:alex-ekwueme-federal-university-ndufu-alike', 'Alex Ekwueme Federal University, Ndufu Alike', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:african-aviation-and-aerospace-university-abuja', 'African Aviation and Aerospace University, Abuja', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:national-university-of-science-and-technology-abuja', 'National University of Science and Technology, Abuja', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-agriculture-mubi', 'Federal University of Agriculture, Mubi', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-agriculture-bassam-bari', 'Federal University of Agriculture, Bassam-Bari', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-agriculture-zuru', 'Federal University of Agriculture, Zuru', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-agriculture-and-technology-obio-akpa', 'Federal University of Agriculture and Technology, Obio-Akpa', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-agriculture-and-developmental-studies-iragbiji', 'Federal University of Agriculture and Developmental Studies, Iragbiji', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-agriculture-and-technology-okeho', 'Federal University of Agriculture and Technology, Okeho', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-medicine-and-medical-sciences-abeokuta', 'Federal University of Medicine and Medical Sciences, Abeokuta', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-health-sciences-kwale', 'Federal University of Health Sciences, Kwale', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-health-sciences-funtua', 'Federal University of Health Sciences, Funtua', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-allied-health-sciences-enugu', 'Federal University of Allied Health Sciences, Enugu', 'FEDERAL', 'Enugu', 'nuc_2026_review'),
  ('ng-university:federal-university-of-applied-sciences-kachia', 'Federal University of Applied Sciences, Kachia', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-science-and-technology-kabo', 'Federal University of Science and Technology, Kabo', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-science-and-technology-epe', 'Federal University of Science and Technology, Epe', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-technology-ikot-abasi', 'Federal University of Technology, Ikot Abasi', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-technology-and-environmental-studies-iyin-ekiti', 'Federal University of Technology and Environmental Studies, Iyin-Ekiti', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-environment-and-technology-tai', 'Federal University of Environment and Technology, Tai', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:federal-university-of-health-science-and-technology-tsafe', 'Federal University of Health Science and Technology, Tsafe', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:university-of-maritime-studies-oron', 'University of Maritime Studies, Oron', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:alvan-ikoku-federal-university-of-education', 'Alvan Ikoku Federal University of Education', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:tai-solarin-federal-university-of-education', 'Tai Solarin Federal University of Education', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:adeyemi-federal-university-of-education', 'Adeyemi Federal University of Education', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:nigerian-army-university-biu', 'Nigerian Army University, Biu', 'FEDERAL', '', 'nuc_2026_review'),
  ('ng-university:air-force-institute-of-technology-kaduna', 'Air Force Institute of Technology, Kaduna', 'FEDERAL', 'Kaduna', 'nuc_2026_review'),
  ('ng-university:adekunle-ajasin-university-akungba-akoko', 'Adekunle Ajasin University, Akungba-Akoko', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:bayelsa-medical-university', 'Bayelsa Medical University', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:bamidele-olumilua-university-of-education-science-and-technology', 'Bamidele Olumilua University of Education, Science and Technology', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:confluence-university-of-science-and-technology', 'Confluence University of Science and Technology', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:cross-river-university-of-education-and-entrepreneurship', 'Cross River University of Education and Entrepreneurship', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:ebonyi-state-university-of-ict-science-and-technology', 'Ebonyi State University of ICT, Science and Technology', 'STATE', 'Ebonyi', 'nuc_2026_review'),
  ('ng-university:ebonyi-state-university-of-aeronautics-and-aerospace-engineering', 'Ebonyi State University of Aeronautics and Aerospace Engineering', 'STATE', 'Ebonyi', 'nuc_2026_review'),
  ('ng-university:enugu-state-university-of-medical-and-applied-sciences', 'Enugu State University of Medical and Applied Sciences', 'STATE', 'Enugu', 'nuc_2026_review'),
  ('ng-university:first-technical-university-ibadan', 'First Technical University, Ibadan', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:gombe-state-university-of-science-and-technology', 'Gombe State University of Science and Technology', 'STATE', 'Gombe', 'nuc_2026_review'),
  ('ng-university:ibrahim-badamasi-babangida-university-lapai', 'Ibrahim Badamasi Babangida University, Lapai', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:jigawa-state-university-of-allied-and-medical-sciences', 'Jigawa State University of Allied and Medical Sciences', 'STATE', 'Jigawa', 'nuc_2026_review'),
  ('ng-university:king-david-umahi-university-of-medical-sciences', 'King David Umahi University of Medical Sciences', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:kingsley-ozumba-mbadiwe-university', 'Kingsley Ozumba Mbadiwe University', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:kwara-state-university-of-education', 'Kwara State University of Education', 'STATE', 'Kwara', 'nuc_2026_review'),
  ('ng-university:lagos-university-of-education', 'Lagos University of Education', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:lagos-university-of-science-and-technology', 'Lagos University of Science and Technology', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:moshood-abiola-university-of-science-and-technology', 'Moshood Abiola University of Science and Technology', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:prince-abubakar-audu-university-anyigba', 'Prince Abubakar Audu University, Anyigba', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:sa-adu-zungur-university', 'Sa''adu Zungur University', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:shehu-shagari-university-of-education', 'Shehu Shagari University of Education', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:taraba-state-university-of-tropical-agriculture-science-technology-and-climate-action', 'Taraba State University of Tropical Agriculture, Science, Technology and Climate Action', 'STATE', 'Taraba', 'nuc_2026_review'),
  ('ng-university:university-of-agriculture-and-environmental-sciences-umuagwo', 'University of Agriculture and Environmental Sciences, Umuagwo', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:university-of-delta-agbor', 'University of Delta, Agbor', 'STATE', 'Delta', 'nuc_2026_review'),
  ('ng-university:university-of-ilesa', 'University of Ilesa', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:university-of-innovation-science-and-technology-omuma', 'University of Innovation, Science and Technology, Omuma', 'STATE', '', 'nuc_2026_review'),
  ('ng-university:university-of-medical-sciences-ondo', 'University of Medical Sciences, Ondo', 'STATE', 'Ondo', 'nuc_2026_review'),
  ('ng-university:yobe-state-university-damaturu', 'Yobe State University, Damaturu', 'STATE', 'Yobe', 'nuc_2026_review'),
  ('ng-university:abdulfattah-durojaiye-university', 'Abdulfattah Durojaiye University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:aletheia-university', 'Aletheia University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:al-ansar-university', 'Al-Ansar University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:al-istiqama-university', 'Al-Istiqama University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:al-bayan-university', 'Al-Bayan University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:al-muhibbah-open-university', 'Al-Muhibbah Open University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:amadeus-university', 'Amadeus University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:amaj-university', 'Amaj University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:anan-university', 'ANAN University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:augustine-university', 'Augustine University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:azman-university', 'Azman University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:azione-verde-university', 'Azione Verde University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:baba-ahmed-university', 'Baba-Ahmed University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:bridget-university-mbaise', 'Bridget University Mbaise', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:british-canadian-university', 'British Canadian University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:canadian-university-of-nigeria', 'Canadian University of Nigeria', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:capital-city-university', 'Capital City University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:chrisland-university', 'Chrisland University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:city-university-abuja', 'City University Abuja', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:claretian-university-of-nigeria', 'Claretian University of Nigeria', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:clifford-university', 'Clifford University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:coal-city-university', 'Coal City University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:cosmopolitan-university', 'Cosmopolitan University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:eko-university-of-medical-sciences', 'Eko University of Medical Sciences', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:el-amin-university', 'El-Amin University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:elrazi-university-of-medical-sciences', 'Elrazi University of Medical Sciences', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:eranova-university', 'Eranova University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:european-university-of-nigeria', 'European University of Nigeria', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:euston-university', 'Euston University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:franco-british-international-university', 'Franco British International University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:gerar-university-of-medical-sciences', 'Gerar University of Medical Sciences', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:godday-erewa-university', 'Godday Erewa University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:greenland-university', 'Greenland University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:havilla-university', 'Havilla University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:hensard-university', 'Hensard University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:high-flyers-university', 'High-Flyers University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:hillside-university-of-science-and-technology', 'Hillside University of Science and Technology', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:huda-university', 'Huda University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:iconic-open-university', 'Iconic Open University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:isaac-balami-university-of-aeronautics-and-management', 'Isaac Balami University of Aeronautics and Management', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:james-hope-university', 'James Hope University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:jefap-university', 'JEFAP University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:jimoh-babalola-university', 'Jimoh Babalola University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:jewel-university', 'Jewel University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:karl-kumm-university', 'Karl-Kumm University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:kevin-eze-university', 'Kevin Eze University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:khalifa-isiyaku-rabiu-university', 'Khalifa Isiyaku Rabiu University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:kwararafa-university', 'Kwararafa University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:legacy-university', 'Legacy University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:lens-university', 'Lens University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:lighthouse-university', 'Lighthouse University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:lux-mundi-university', 'Lux Mundi University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:maduka-university', 'Maduka University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:maranatha-university', 'Maranatha University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:margaret-lawrence-university', 'Margaret Lawrence University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:maria-assumpta-university', 'Maria Assumpta University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:maryam-abacha-american-university-of-nigeria', 'Maryam Abacha American University of Nigeria', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:michael-and-cecilia-ibru-university', 'Michael and Cecilia Ibru University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:millennium-crest-university', 'Millennium Crest University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:minaret-university', 'Minaret University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:monarch-university', 'Monarch University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:mudiame-university', 'Mudiame University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:new-city-university', 'New City University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:newgate-university', 'Newgate University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:north-eastern-university-gombe', 'North Eastern University, Gombe', 'PRIVATE', 'Gombe', 'nuc_2026_review'),
  ('ng-university:northwest-university-sokoto', 'Northwest University, Sokoto', 'PRIVATE', 'Sokoto', 'nuc_2026_review'),
  ('ng-university:obong-university', 'Obong University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:ojaja-university', 'Ojaja University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:omega-university-kaduna', 'Omega University Kaduna', 'PRIVATE', 'Kaduna', 'nuc_2026_review'),
  ('ng-university:owolabi-university', 'Owolabi University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:peaceland-university', 'Peaceland University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:pearl-university', 'Pearl University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:pen-resource-university', 'Pen Resource University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:peter-university', 'Peter University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:philomath-university', 'Philomath University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:phoenix-university', 'Phoenix University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:prime-university', 'Prime University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:rayhaan-university', 'Rayhaan University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:regnum-medical-university', 'Regnum Medical University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:ritman-university', 'Ritman University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:saisa-university-of-medical-sciences-and-technology', 'Saisa University of Medical Sciences and Technology', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:sam-maris-university', 'Sam Maris University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:sani-bello-university', 'Sani Bello University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:shanahan-university', 'Shanahan University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:southern-atlantic-university', 'Southern Atlantic University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:spiritan-university', 'Spiritan University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:sports-university', 'Sports University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:tazkiyah-university', 'Tazkiyah University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:the-duke-medical-university-calabar', 'The Duke Medical University, Calabar', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:tonnie-iredia-university-of-communication', 'Tonnie Iredia University of Communication', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:topfaith-university', 'Topfaith University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:transatlantic-university-of-medicine-and-health-sciences', 'Transatlantic University of Medicine and Health Sciences', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:ummah-university-of-nigeria', 'Ummah University of Nigeria', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:unique-open-university', 'Unique Open University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:university-of-fortune', 'University of Fortune', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:university-of-mkar', 'University of Mkar', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:university-of-offa', 'University of Offa', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:university-on-the-niger', 'University on the Niger', 'PRIVATE', 'Niger', 'nuc_2026_review'),
  ('ng-university:vision-university', 'Vision University', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:wesley-university-of-science-and-technology', 'Wesley University of Science and Technology', 'PRIVATE', '', 'nuc_2026_review'),
  ('ng-university:west-midland-open-university', 'West Midland Open University', 'PRIVATE', '', 'nuc_2026_review')
on conflict(id) do update
  set official_name = excluded.official_name,
      ownership = excluded.ownership,
      state = excluded.state,
      source_label = excluded.source_label,
      updated_at = now();

alter table public.blink_universities enable row level security;
revoke all on public.blink_universities from public, anon, authenticated;
grant select on public.blink_universities to anon, authenticated;

drop policy if exists "Read active university directory" on public.blink_universities;
create policy "Read active university directory"
on public.blink_universities for select to anon, authenticated
using (active);

alter table public.profiles
  add column if not exists university_id text
  references public.blink_universities(id);

create index if not exists idx_blink_profiles_university_id
  on public.profiles(university_id);

-- Backfill only exact institutional names. Nonmatching historical names are
-- not guessed; users can choose the verified directory entry when editing.
update public.profiles p
set university_id = u.id
from public.blink_universities u
where p.university_id is null
  and lower(trim(p.university)) = lower(trim(u.official_name));

-- New writes always pair the selected institution ID with its recognized name.
-- Old clients without an ID continue to work until they adopt the picker.
create or replace function public.enforce_blink_profile_university_id()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_name text;
begin
  if new.university_id is not null then
    select u.official_name into v_name from public.blink_universities u
    where u.id = new.university_id and u.active;
    if v_name is null or lower(trim(coalesce(new.university,''))) <> lower(trim(v_name)) then
      raise exception 'UNIVERSITY_SELECTION_MISMATCH';
    end if;
  end if;
  return new;
end; $$;

drop trigger if exists trg_enforce_blink_profile_university_id on public.profiles;
create trigger trg_enforce_blink_profile_university_id
before insert or update of university, university_id
on public.profiles for each row
execute function public.enforce_blink_profile_university_id();

create table if not exists public.blink_missing_university_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  suggested_name text not null,
  normalized_name text not null,
  state text not null default '',
  review_status text not null default 'pending'
    check (review_status in ('pending','approved','rejected')),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewer_id uuid references auth.users(id)
);

create unique index if not exists idx_university_request_per_user
  on public.blink_missing_university_requests(user_id, normalized_name);
create index if not exists idx_university_request_review
  on public.blink_missing_university_requests(review_status, created_at);

alter table public.blink_missing_university_requests enable row level security;
revoke all on public.blink_missing_university_requests from public, anon, authenticated;

create or replace function public.request_missing_blink_university(
  p_name text, p_state text default ''
) returns text language plpgsql security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid(); v_name text; v_key text;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  v_name := trim(regexp_replace(coalesce(p_name,''), '[[:space:]]+', ' ', 'g'));
  if char_length(v_name) < 8 or char_length(v_name) > 120 then
    raise exception 'INVALID_INSTITUTION_NAME';
  end if;
  if p_state is null or char_length(p_state) > 60 then
    raise exception 'INVALID_STATE';
  end if;
  v_key := lower(v_name);
  if exists (
    select 1 from public.blink_universities u
    where lower(u.official_name) = v_key
  ) then return 'already_listed'; end if;

  if exists (
    select 1 from public.blink_missing_university_requests r
    where r.user_id = v_uid and r.normalized_name = v_key
  ) then return 'already_requested'; end if;

  if (
    select count(*) from public.blink_missing_university_requests r
    where r.user_id = v_uid and r.created_at >= now() - interval '1 day'
  ) >= 3 then raise exception 'REQUEST_DAILY_LIMIT'; end if;

  insert into public.blink_missing_university_requests
    (user_id, suggested_name, normalized_name, state)
  values (v_uid, v_name, v_key, trim(p_state));
  return 'pending_review';
end; $$;

revoke all on function public.request_missing_blink_university(text, text)
from public, anon;
grant execute on function public.request_missing_blink_university(text, text)
to authenticated;

comment on table public.blink_missing_university_requests is
  'User suggestions requiring admin review against NUC; requests never automatically create a university.';

-- Read-only directory for app clients; review queue remains service-only.
grant all on public.blink_universities, public.blink_missing_university_requests to service_role;

commit;
