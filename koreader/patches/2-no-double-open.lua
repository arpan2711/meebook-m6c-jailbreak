--[[--
Don't re-open the book that is already open.

On every resume KOReader checks for an "open this file" intent (InkHome's cover tap, or the intent that
started the activity) and calls ReaderUI:doShowReader(file) even when that file is already on screen. That
spun up a second reader ("ReaderUI instance mismatch!" in the log, 33 times): slower, and an extra failure
path. Now a request for the open file is ignored, so KOReader simply stays on the current page.
Reloads still work (KOReader closes the reader before reopening). Delete this file to undo.
--]]

local ReaderUI = require("apps/reader/readerui")
local ffiUtil = require("ffi/util")
local logger = require("logger")

local function same(a, b)
    if not a or not b then return false end
    return a == b or (ffiUtil.realpath(a) or a) == (ffiUtil.realpath(b) or b)
end

local orig_doShowReader = ReaderUI.doShowReader

function ReaderUI:doShowReader(file, provider, seamless)
    local current = ReaderUI.instance
    if current and current.document and same(current.document.file, file) then
        logger.info("no-double-open: already showing", file)
        return
    end
    return orig_doShowReader(self, file, provider, seamless)
end
