--[[--
Menus open as just their icon row.

Top (tabbed) menu: opens showing only the tab icons; tapping an icon drops its list down.
Bottom reading-settings panel: opens showing only its icon bar; tapping an icon opens that panel.
After the first tap both behave exactly as stock. Delete this file to undo.
--]]

local BottomContainer = require("ui/widget/container/bottomcontainer")
local ConfigDialog = require("ui/widget/configdialog")
local FrameContainer = require("ui/widget/container/framecontainer")
local Screen = require("device").screen
local TouchMenu = require("ui/widget/touchmenu")
local VerticalGroup = require("ui/widget/verticalgroup")
local Blitbuffer = require("ffi/blitbuffer")

-- Top menu ------------------------------------------------------------------

local orig_tm_init = TouchMenu.init
function TouchMenu:init()
    self._icons_only_pending = true  -- the tab auto-selected during init stays collapsed
    orig_tm_init(self)
end

local orig_tm_switch = TouchMenu.switchMenuTab
function TouchMenu:switchMenuTab(tab_num)
    if self._icons_only_pending then
        self._icons_only_pending = nil
        self._icons_only = true
    else
        self._icons_only = nil
    end
    return orig_tm_switch(self, tab_num)
end

local orig_tm_update = TouchMenu.updateItems
function TouchMenu:updateItems(...)
    orig_tm_update(self, ...)
    if not self._icons_only then return end
    -- keep only the icon bar; setDirty's callback reads self.dimen later, so this is what gets painted
    self.item_group:clear()
    table.insert(self.item_group, self.bar)
    self.layout = { self.bar.icon_widgets }
    self.dimen.h = self.item_group:getSize().h + self.bordersize*2 + self.padding
end

-- Bottom settings panel -----------------------------------------------------

local orig_cd_init = ConfigDialog.init
function ConfigDialog:init()
    self._icons_only_pending = true  -- ReaderConfig opens the last panel right after creating us
    orig_cd_init(self)
end

local orig_cd_show = ConfigDialog.onShowConfigPanel
function ConfigDialog:onShowConfigPanel(index)
    if self._icons_only_pending then
        self._icons_only_pending = nil
        self._icons_only = true
    else
        self._icons_only = nil
    end
    return orig_cd_show(self, index)
end

local orig_cd_update = ConfigDialog.update
function ConfigDialog:update()
    orig_cd_update(self)
    if not self._icons_only then return end
    self.dialog_frame = FrameContainer:new{
        background = Blitbuffer.COLOR_WHITE,
        padding_bottom = 0,
        VerticalGroup:new{ self.config_menubar },
    }
    self[1] = BottomContainer:new{
        dimen = Screen:getSize(),
        self.dialog_frame,
    }
end
