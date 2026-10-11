--[[--
ONE-SHOT: extract covers + metadata for every book under /storage/emulated/0/Books into the cover browser
cache, in KOReader's own background extractor (BookInfoManager:extractInBackground, a subprocess).
Runs once (marker file settings/meebook-precache.done), 8 s after start. Remove this file afterwards.
Progress is logged as "precache:" in logcat.
--]]

local DataStorage = require("datastorage")
local UIManager = require("ui/uimanager")
local lfs = require("libs/libkoreader-lfs")
local logger = require("logger")

local ROOT = "/storage/emulated/0/Books"
local MARKER = DataStorage:getSettingsDir() .. "/meebook-precache.done"
local EXTS = { epub = true, mobi = true, pdf = true, djvu = true, txt = true, fb2 = true, azw3 = true, cbz = true }
local SPECS = { max_cover_w = 383, max_cover_h = 383 }  -- same size the existing cache entries use

if lfs.attributes(MARKER, "mode") then return end

local function collect(dir, out)
    for name in lfs.dir(dir) do
        if name ~= "." and name ~= ".." and not name:match("^%.") and not name:match("%.sdr$") then
            local path = dir .. "/" .. name
            local mode = lfs.attributes(path, "mode")
            if mode == "directory" then
                collect(path, out)
            elseif mode == "file" and EXTS[(name:match("%.([^.]+)$") or ""):lower()] then
                table.insert(out, path)
            end
        end
    end
end

UIManager:scheduleIn(8, function()
    local ok, BookInfoManager = pcall(require, "bookinfomanager")
    if not ok then
        logger.warn("precache: coverbrowser not loaded", BookInfoManager)
        return
    end
    local all = {}
    collect(ROOT, all)
    table.sort(all)
    local files = {}
    for _, path in ipairs(all) do
        local info = BookInfoManager:getBookInfo(path, false)
        if not (info and info.cover_fetched) then
            table.insert(files, { filepath = path, cover_specs = SPECS })
        end
    end
    logger.info("precache: books found", #all, "to extract", #files)
    local f = io.open(MARKER, "w")
    if f then f:write(os.date() .. " queued " .. #files .. "\n") f:close() end
    BookInfoManager:extractInBackground(files)
end)
