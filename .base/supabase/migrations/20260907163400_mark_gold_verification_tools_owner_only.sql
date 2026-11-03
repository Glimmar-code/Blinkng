update private.admin_feature_registry_v2
set owner_only = true,
    description = case feature_id
      when 102 then 'Overall owner only: grant Gold verification.'
      when 106 then 'Overall owner only: change Blue verification to Gold.'
      else description
    end
where feature_id in (102,106);