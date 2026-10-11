--[[--
A locked status bar ignores taps completely.

Stock "Lock status bar" only stops a tap from cycling the bar's items; the tap then falls through to the
bottom tap zone and opens the reading menus. With this patch a tap on the locked bar does nothing.
(Taps just above the bar still open the menus. In page-flipping mode the bar still jumps to the tapped spot.)
Delete this file to undo.
--]]

local ReaderFooter = require("apps/reader/modules/readerfooter")

local orig_TapFooter = ReaderFooter.TapFooter

function ReaderFooter:TapFooter(ges)
    if self.settings.lock_tap and not (self.view.flipping_visible and ges) then
        return true  -- swallow the tap
    end
    return orig_TapFooter(self, ges)
end
