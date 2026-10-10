-- /storage/emulated/0/koreader/defaults.custom.lua
-- Both menus live in the bottom strip, so a swipe down from the top edge only reveals
-- Android's status bar: tap bottom-left = main menu, tap bottom-right = reading settings.
return {
    ["DTAP_ZONE_MENU"] = {x = 0, y = 7/8, w = 1/2, h = 1/8},
    ["DTAP_ZONE_MENU_EXT"] = {x = 0, y = 7/8, w = 1/2, h = 1/8},
    ["DTAP_ZONE_CONFIG"] = {x = 1/2, y = 7/8, w = 1/2, h = 1/8},
    ["DTAP_ZONE_CONFIG_EXT"] = {x = 1/2, y = 7/8, w = 1/2, h = 1/8},
}
