package dev.bing.tokenmonitor

// Desktop v0.68.0 weekStartsOn/Intl rules; regenerated with capture-fixed-periods.cjs.
internal val desktopRegionWeekStarts = buildMap<String, Int> {
    "AF BH DJ DZ EG IQ IR JO KW LY OM QA SD SY 004 012 048 262 364 368 400 414 434 512 634 729 736 760 818".split(" ").forEach { put(it, 6) }
    "AG AS BD BR BS BT BU BW BZ CA CO DM DO ET GT GU HK HN ID IL IN IS JM JP JT KE KH KR LA MH MI MM MO MT MX MZ NI NP NT PA PE PH PK PR PT PU PY PZ RH SA SG SV TH TT TW UM US VE VI WK WS YD YE ZA ZW 016 028 044 050 064 072 076 084 104 116 124 158 170 212 214 222 230 231 316 320 340 344 352 356 360 376 388 392 404 410 418 446 470 484 508 524 536 558 581 584 586 591 600 604 608 620 630 682 702 710 716 720 764 780 840 850 862 882 886 887".split(" ").forEach { put(it, 7) }
    "MV 462".split(" ").forEach { put(it, 5) }
}

internal val desktopLanguageWeekStarts = buildMap<String, Int> {
    "ae ar fa ps".split(" ").forEach { put(it, 6) }
    "aa af am as bh bn ch cr dz en gn gu he hi id ik in is iu iw ja jv jw ki km kn ko ks lo mh ml mr mt my nd ne nr nv oj om or pa pt qu sa sd sm sn ss st su ta te th ti tl tn ts ur ve xh zu".split(" ").forEach { put(it, 7) }
}
