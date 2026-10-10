--[[--
Keeps KOReader's favourites in step with the InkHome launcher.

1. KOReader only writes collection.lua when the file browser (or reader) closes. On
   Android, pressing Home just backgrounds KOReader, so a new favourite stayed in
   memory. Every collection change is now written immediately.
2. KOReader only reads collection.lua at startup and later overwrites it from memory,
   which would undo a favourite removed on the home screen. The file is re-read (only
   if it changed) before collections are queried, changed or shown.
3. Adds "Add to favorites" / "Remove from favorites" to the file browser's long-press
   dialog, one tap instead of going through Collections.
--]]

local ReadCollection = require("readcollection")
local UIManager = require("ui/uimanager")
local WidgetContainer = require("ui/widget/container/widgetcontainer")
local logger = require("logger")
local _ = require("gettext")

local FAVORITES = ReadCollection.default_collection_name or "favorites"

local function reload()
    -- _read() is a no-op unless collection.lua is newer than the last read
    local ok, err = pcall(ReadCollection._read, ReadCollection)
    if not ok then logger.warn("favsync: reload failed", err) end
end

if not ReadCollection._favsync_wrapped then
    local writers = { addItem = true, addRemoveItemMultiple = true, addItemsMultiple = true, removeItem = true }
    local readers = { "isFileInCollection", "isFileInCollections", "getCollectionsWithFile", "getCollectionNextOrder" }
    for name in pairs(writers) do
        local original = ReadCollection[name]
        if original then
            ReadCollection[name] = function(self, ...)
                reload()
                -- LuaJIT (Lua 5.1): no table.pack/unpack; these functions return at most one value
                local result = original(self, ...)
                local ok, err = pcall(self.write, self)  -- write all: removeItem's own write targets a wrong key
                if not ok then logger.warn("favsync: write failed", err) end
                return result
            end
        end
    end
    for _, name in ipairs(readers) do
        local original = ReadCollection[name]
        if original then
            ReadCollection[name] = function(self, ...)
                reload()
                return original(self, ...)
            end
        end
    end
    ReadCollection._favsync_wrapped = true
end

local FavSync = WidgetContainer:extend{
    name = "favsync",
}

function FavSync:init()
    local fm = self.ui
    if fm and fm.addFileDialogButtons then  -- file browser only
        fm:addFileDialogButtons("favsync_favorite", function(file, is_file)
            if not is_file then return end
            local in_fav = ReadCollection:isFileInCollection(file, FAVORITES)
            return {{
                text = in_fav and _("Remove from favorites") or _("Add to favorites"),
                callback = function()
                    if fm.file_dialog then UIManager:close(fm.file_dialog) end
                    if in_fav then
                        ReadCollection:removeItem(file, FAVORITES)
                    else
                        ReadCollection:addItem(file, FAVORITES)
                    end
                    if fm.file_chooser then fm.file_chooser:refreshPath() end
                end,
            }}
        end)
    end
    -- the Favorites/collections view reads ReadCollection.coll directly
    local coll_module = fm and fm.collections
    if coll_module and not coll_module._favsync_wrapped and coll_module.onShowColl then
        local show = coll_module.onShowColl
        coll_module.onShowColl = function(this, ...)
            reload()
            return show(this, ...)
        end
        coll_module._favsync_wrapped = true
    end
end

return FavSync
