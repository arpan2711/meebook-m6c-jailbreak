--[[--
One tap anywhere along the bottom strip opens every menu at once: the reading settings
panel at the bottom and the main (tabbed) menu at the top. Combined with
activate_menu = "tap" and the menu zones moved off the top edge (defaults.custom.lua),
a swipe down from the top only reveals Android's status bar.
--]]

local Event = require("ui/event")
local WidgetContainer = require("ui/widget/container/widgetcontainer")

local BottomMenus = WidgetContainer:extend{
    name = "bottommenus",
}

function BottomMenus:init()
    -- reader only (the file browser has its own menu handling)
    if not (self.ui and self.ui.document and self.ui.menu and self.ui.config) then return end
    self.ui:registerTouchZones({
        {
            id = "bottommenus_tap",
            ges = "tap",
            screen_zone = { ratio_x = 0, ratio_y = 7/8, ratio_w = 1, ratio_h = 1/8 },
            overrides = {
                "readermenu_tap", "readermenu_ext_tap",
                "readerconfigmenu_tap", "readerconfigmenu_ext_tap",
                "tap_forward", "tap_backward",
            },
            handler = function()
                self.ui:handleEvent(Event:new("ShowConfigMenu"))  -- reading settings (bottom)
                self.ui.menu:onShowMenu()                         -- main menu (top), shown last so it is on top
                return true
            end,
        },
    })
end

return BottomMenus
