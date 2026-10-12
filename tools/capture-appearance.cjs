// Fixed desktop pure modules only; does not read user settings or make network requests.
const fs=require('node:fs');const path=require('node:path');
const root=path.resolve(__dirname,'../../work/token-monitor-reference/desktop-v0.68.0');
const theme=require(path.join(root,'src/electron/renderer/themePresets.js'));
const font=require(path.join(root,'src/shared/fontSettings.js'));
const colors=[{},...theme.THEME_PRESETS.map(p=>p.colors),{chart:'#ff0000'},{chart:'#123456',accent:'#ABCDEF',bg:'#FFFFFF'},{chart:'#000000'}, {chart:'#73BDF5',bad:'#123456',bg:'bogus'}];
const codes=['','TM1-B7EAD4-303438-EEF5FB-A3ADBB','tm1-2563eb-f6f7f9-1c1f26-5b626d',' TM2-ABCDEF-303438-EEF5FB-A3ADBB-FF0000 ','TM3-AABBCC','TM01-AAAAAA','TM1-112233-445566-778899-AABBCC-123456','TM2-112233-445566-778899-AABBCC','TM1-GGGGGG-445566-778899-AABBCC','TM2-112233-445566-778899-AABBCC-000000'];
const families=['',' ','Roboto',' serif ','"Noto Sans CJK", sans-serif','PingFang SC,  monospace','A\nB','A\tB','A\u00a0B','A\ufeffB','A; color: red','<b>','{x}','a\u0000b','a\u007fb','x'.repeat(160),'x'.repeat(161),font.DEFAULT_INTERFACE_FONT,font.DEFAULT_DISPLAY_FONT,font.SYSTEM_UI_FONT];
const result={source:'v0.68.0/5d2db368d8313415763860d594de00e46a663418; pure themePresets/fontSettings',
 presets:theme.THEME_PRESETS,
 colors:colors.map(input=>({input,clean:theme.normalizeOverrides(input,theme.INTERFACE_COLOR_KEYS),palette:theme.mergeThemeColors(input),code:theme.encodeThemeCode(input),light:theme.isLightHex(theme.mergeThemeColors(input).bg),heat:theme.themeCssVarEntries(input).filter(v=>/^--chart-heat-\d-rgb$/.test(v.name))})),
 codes:codes.map(input=>({input,expected:theme.decodeThemeCode(input)})),
 fonts:families.map(input=>({input,normalized:font.normalizeFontFamily(input),interface:font.presetForFontFamily(input),display:font.presetForFontFamily(input,'display')})),
 sizes:['standard','larger','largest','invalid',''].map(input=>({input,scale:font.textScaleForSize(input)})),
 fontPresets:['app','system','mono','custom','follow','invalid'].flatMap(preset=>['interface','display'].map(role=>({preset,role,value:font.fontFamilyForPreset(preset,' Noto Sans ',role)})))};
const output=path.join(__dirname,'../app/src/test/resources/appearance-parity.json');
fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n');
process.stdout.write(`Captured ${colors.length+codes.length+families.length+result.sizes.length+result.fontPresets.length+result.presets.length} public desktop appearance cases\n`);
