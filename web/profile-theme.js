/* Shared public profile palette and edit controls. */
(function(){
 'use strict';
  const PROFILE_THEME_PALETTES = Object.freeze({
    black: {title:'Pure Black',top:'#000000',bottom:'#000000',card:'#111111',accent:'#ffffff'},
    royal_purple: {title:'Royal Purple',top:'#52146b',bottom:'#160b23',card:'#391744',accent:'#d16fff'},
    sapphire: {title:'Sapphire Blue',top:'#123f88',bottom:'#081326',card:'#132c52',accent:'#75b7ff'},
    emerald: {title:'Emerald',top:'#126252',bottom:'#081f1a',card:'#16443a',accent:'#7ae7be'},
    rose: {title:'Rose',top:'#76254e',bottom:'#260e1d',card:'#51203c',accent:'#ff91c9'},
    amber: {title:'Amber',top:'#77501a',bottom:'#261909',card:'#4d3415',accent:'#ffcd75'}
  });
  function themeId(value){return Object.hasOwn(PROFILE_THEME_PALETTES,String(value||'').trim())?value:'black';}
  function ownerThemeCss(value){
    const p=PROFILE_THEME_PALETTES[themeId(value)];
    return `--profile-theme-top:${p.top};--profile-theme-bottom:${p.bottom};--profile-theme-card:${p.card};--profile-theme-accent:${p.accent};`;
  }
  function ownerThemePicker(value){
    const selected=themeId(value);
    return `<label class="form-label">Profile theme<select class="field" name="profile_theme">${Object.entries(PROFILE_THEME_PALETTES).map(([id,p])=>`<option value="${id}" ${id===selected?'selected':''}>${p.title}</option>`).join('')}</select></label>`;
  }

  window.BlinkProfileTheme=Object.freeze({themeId,ownerThemeCss,ownerThemePicker});
})();
