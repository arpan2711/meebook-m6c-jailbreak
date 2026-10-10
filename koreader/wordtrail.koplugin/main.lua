--[[--
Word trail: a richer companion to the vocabulary builder.

When "Add to vocabulary builder" is tapped, the word is also stored in
WordTrail/wordtrail.sqlite3 with the book, authors, file, exact position
(xpointer or page), the sentence and the whole paragraph. A word can have many
sources. The word list (main menu > Word trail) jumps back to any source.

The database lives in its own folder so it can be synced to the PC (Syncthing).
It uses the default rollback journal (not WAL) so the single .sqlite3 file is
always consistent when a sync tool copies it.
--]]

local DataStorage = require("datastorage")
local Device = require("device")
local Event = require("ui/event")
local InfoMessage = require("ui/widget/infomessage")
local Menu = require("ui/widget/menu")
local TextViewer = require("ui/widget/textviewer")
local UIManager = require("ui/uimanager")
local WidgetContainer = require("ui/widget/container/widgetcontainer")
local logger = require("logger")
local lfs = require("libs/libkoreader-lfs")
local util = require("util")
local SQ3 = require("lua-ljsqlite3/init")
local _ = require("gettext")
local T = require("ffi/util").template

local function data_dir()
    local shared = "/storage/emulated/0/WordTrail"
    if Device:isAndroid() then
        if lfs.attributes(shared, "mode") ~= "directory" then lfs.mkdir(shared) end
        if lfs.attributes(shared, "mode") == "directory" then return shared end
    end
    local dir = DataStorage:getDataDir() .. "/wordtrail"
    if lfs.attributes(dir, "mode") ~= "directory" then lfs.mkdir(dir) end
    return dir
end

local DB_PATH = data_dir() .. "/wordtrail.sqlite3"
local SCHEMA = [[
CREATE TABLE IF NOT EXISTS lookups (
    id           INTEGER PRIMARY KEY,
    word         TEXT NOT NULL,
    book_title   TEXT,
    authors      TEXT,
    file         TEXT,
    position     TEXT,      -- crengine xpointer, or "page:N" for PDF/DjVu
    page         INTEGER,
    pages        INTEGER,
    sentence     TEXT,
    paragraph    TEXT,
    prev_context TEXT,
    next_context TEXT,
    created      INTEGER NOT NULL,
    source       TEXT       -- "reader" or "vocabbuilder-import"
);
CREATE INDEX IF NOT EXISTS lookups_word ON lookups(word);
CREATE INDEX IF NOT EXISTS lookups_created ON lookups(created);
]]

local function open_db()
    local conn = SQ3.open(DB_PATH)
    conn:exec("PRAGMA journal_mode=DELETE;")
    conn:exec(SCHEMA)
    return conn
end

local function trim(s)
    return s and (s:gsub("^%s+", ""):gsub("%s+$", "")) or s
end

local function collapse(s)
    return s and trim(s:gsub("%s+", " ")) or s
end

-- Sentence = tail of the previous context after the last sentence end + word + head of the next context.
local function build_sentence(prev, word, next_)
    prev, next_ = prev or "", next_ or ""
    local head = prev:match(".*[%.%!%?…][\"'”’%)]*%s+(.*)$") or prev
    local tail = next_:match("^(.-[%.%!%?…][\"'”’%)]*)%s") or next_:match("^(.-[%.%!%?…][\"'”’%)]*)$") or next_
    local sep_before = (head ~= "" and not head:match("%s$")) and " " or ""
    local sep_after = (tail ~= "" and not tail:match("^[%s%p]")) and " " or ""
    return collapse(head .. sep_before .. word .. sep_after .. tail)
end

local WordTrail = WidgetContainer:extend{
    name = "wordtrail",
}

function WordTrail:init()
    self.ui.menu:registerToMainMenu(self)
    local ok, err = pcall(function() open_db():close() end)
    if not ok then logger.warn("WordTrail: cannot open db", err) end
    self:importVocabBuilderOnce()
end

function WordTrail:addToMainMenu(menu_items)
    menu_items.wordtrail = {
        text = _("Word trail"),
        sorting_hint = "search",  -- next to the vocabulary builder
        callback = function() self:showWordList() end,
    }
end

-- Wrap the vocabulary builder's handler: it returns true and stops the event,
-- so listening to "WordLookedUp" ourselves would never fire after it.
function WordTrail:onReaderReady()
    local vb = self.ui.vocabbuilder
    if vb and not vb._wordtrail_wrapped then
        local original = vb.onWordLookedUp
        local trail = self
        vb.onWordLookedUp = function(vb_self, word, title, is_manual)
            if is_manual then
                local ok, err = pcall(trail.capture, trail, word, title)
                if not ok then logger.warn("WordTrail: capture failed", err) end
            end
            return original(vb_self, word, title, is_manual)
        end
        vb._wordtrail_wrapped = true
    end
end

function WordTrail:capture(word, title)
    local ui = self.ui
    if not (ui and ui.document) then return end
    local sel = ui.highlight and ui.highlight.selected_text
    local position, paragraph
    local prev, next_
    if ui.highlight then  -- not `a and f()`: that would keep only f's first return value
        prev, next_ = ui.highlight:getSelectedWordContext(60)
    end
    if sel and type(sel.pos0) == "string" then
        position = sel.pos0
        local ok, html = pcall(ui.document.getHTMLFromXPointer, ui.document, sel.pos0, 0, true)
        if ok and type(html) == "string" then
            paragraph = collapse(util.htmlToPlainTextIfHtml(html))
        end
    end
    local page = ui:getCurrentPage()
    if not position then position = "page:" .. tostring(page) end
    local shown = (sel and sel.text and util.cleanupSelectedText(sel.text)) or word
    local sentence = build_sentence(prev, shown, next_)
    if not paragraph or paragraph == "" or #paragraph > 4000 then
        paragraph = collapse((prev or "") .. " " .. shown .. " " .. (next_ or ""))
    end
    local props = ui.doc_props or {}
    local conn = open_db()
    local check = conn:prepare("SELECT COUNT(*) FROM lookups WHERE word = ? AND file = ? AND position = ?;")
    check:bind(word, ui.document.file, position)
    local seen = check:step()
    check:close()
    if seen and tonumber(seen[1]) > 0 then  -- same word from the same spot: already saved
        conn:close()
        return
    end
    local stmt = conn:prepare([[INSERT INTO lookups
        (word, book_title, authors, file, position, page, pages, sentence, paragraph,
         prev_context, next_context, created, source)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'reader');]])
    stmt:bind(word, title or props.display_title, props.authors, ui.document.file, position, page,
        ui.document:getPageCount(), sentence, paragraph, prev, next_, os.time())
    stmt:step()
    stmt:close()
    conn:close()
end

function WordTrail:onDictButtonsReady(dict_popup, buttons)
    if dict_popup.is_wiki_fullpage then return end
    table.insert(buttons, 1, {{
        id = "wordtrail_ddg",
        text = _("Search DuckDuckGo"),
        font_bold = false,
        callback = function()
            local q = util.urlEncode(dict_popup.lookupword or dict_popup.word or "")
            -- the html. endpoint answers direct links with a bot check; the normal page works
            Device:openLink("https://duckduckgo.com/?q=" .. q)
        end,
    }})
end

-- One-time import of the existing vocabulary builder words (no positions, so no jump-back).
function WordTrail:importVocabBuilderOnce()
    -- The database itself records whether the import happened (a reader setting
    -- would be lost if KOReader is killed before it flushes its settings).
    local ok_count, done = pcall(function()
        local conn = open_db()
        local n = tonumber(conn:rowexec("SELECT COUNT(*) FROM lookups WHERE source = 'vocabbuilder-import';"))
        conn:close()
        return n and n > 0
    end)
    if not ok_count or done then return end
    local vb_path = DataStorage:getSettingsDir() .. "/vocabulary_builder.sqlite3"
    if lfs.attributes(vb_path, "mode") ~= "file" then return end
    local ok, err = pcall(function()
        local src = SQ3.open(vb_path)
        local rows = src:exec([[SELECT v.word, t.name, v.prev_context, v.next_context, v.create_time
            FROM vocabulary v LEFT JOIN title t ON v.title_id = t.id;]])
        src:close()
        if not rows then return end
        local conn = open_db()
        local stmt = conn:prepare([[INSERT INTO lookups
            (word, book_title, sentence, paragraph, prev_context, next_context, created, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'vocabbuilder-import');]])
        for i = 1, #rows[1] do
            local w, prev, nxt = rows[1][i], rows[3][i], rows[4][i]
            stmt:bind(w, rows[2][i], build_sentence(prev, w, nxt),
                collapse((prev or "") .. " " .. w .. " " .. (nxt or "")), prev, nxt, tonumber(rows[5][i]) or os.time())
            stmt:step()
            stmt:clearbind():reset()
        end
        stmt:close()
        conn:close()
    end)
    if not ok then logger.warn("WordTrail: import failed", err) end
end

function WordTrail:query(sql, ...)
    local conn = open_db()
    local stmt = conn:prepare(sql)
    if select("#", ...) > 0 then stmt:bind(...) end
    -- ljsqlite3 rows are positional; the header gives the column names
    local out = {}
    local row, header = stmt:step({}, {})
    while row do
        local named = {}
        for i, name in ipairs(header) do named[name] = row[i] end
        table.insert(out, named)
        row, header = stmt:step({}, {})
    end
    stmt:close()
    conn:close()
    return out
end

function WordTrail:showWordList()
    local rows = self:query([[SELECT word, COUNT(*) AS n, MAX(created) AS last
        FROM lookups GROUP BY word ORDER BY last DESC;]])
    local items = {}
    for _idx, r in ipairs(rows) do
        local word, n = r.word, tonumber(r.n)
        table.insert(items, {
            text = word,
            mandatory = n > 1 and T(_("%1 sources"), n) or nil,
            callback = function() self:showSources(word) end,
        })
    end
    if #items == 0 then
        UIManager:show(InfoMessage:new{ text = _("No words yet. Use \"Add to vocabulary builder\" in the dictionary popup.") })
        return
    end
    self.word_menu = Menu:new{
        title = T(_("Word trail (%1 words)"), #items),
        item_table = items,
        covers_fullscreen = true,
        is_borderless = true,
        is_popout = false,
    }
    UIManager:show(self.word_menu)
end

function WordTrail:showSources(word)
    local rows = self:query([[SELECT id, book_title, file, position, page, sentence, paragraph
        FROM lookups WHERE word = ? ORDER BY created DESC;]], word)
    local items = {}
    for _idx, r in ipairs(rows) do
        local src = r
        local can_jump = src.file and src.file ~= "" and lfs.attributes(src.file, "mode") == "file"
        table.insert(items, {
            text = (src.book_title or _("Unknown book")) .. " — " .. (src.sentence or ""),
            mandatory = can_jump and _("open") or nil,
            callback = function()
                if can_jump then
                    self:jumpTo(src)
                else
                    self:showParagraph(word, src)
                end
            end,
        })
    end
    local menu
    menu = Menu:new{
        title = word,
        item_table = items,
        covers_fullscreen = true,
        is_borderless = true,
        is_popout = false,
        onMenuHold = function(_menu, item)
            local idx
            for i, it in ipairs(items) do if it == item then idx = i end end
            if idx then self:showParagraph(word, rows[idx]) end
            return true
        end,
    }
    self.source_menu = menu
    UIManager:show(menu)
end

function WordTrail:showParagraph(word, src)
    UIManager:show(TextViewer:new{
        title = word .. " — " .. (src.book_title or ""),
        text = src.paragraph or src.sentence or "",
    })
end

local function goto_position(ui, src)
    if ui.link then ui.link:addCurrentLocationToStack() end
    if ui.rolling and src.position and src.position:sub(1, 1) == "/" then
        ui:handleEvent(Event:new("GotoXPointer", src.position, src.position))
    elseif src.page then
        ui:handleEvent(Event:new("GotoPage", tonumber(src.page)))
    end
end

function WordTrail:jumpTo(src)
    if self.source_menu then UIManager:close(self.source_menu) end
    if self.word_menu then UIManager:close(self.word_menu) end
    local ui = self.ui
    if ui.document and ui.document.file == src.file then
        goto_position(ui, src)
    elseif ui.document then
        ui:switchDocument(src.file, true, function(new_ui) goto_position(new_ui, src) end)
    else
        local ReaderUI = require("apps/reader/readerui")
        ReaderUI.after_open_callback = function(new_ui) goto_position(new_ui, src) end
        ReaderUI:showReader(src.file)
    end
end

return WordTrail
