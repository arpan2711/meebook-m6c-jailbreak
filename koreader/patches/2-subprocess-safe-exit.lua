--[[--
Background subprocesses exit with _exit(), not exit(). Fixes KOReader opening to a black screen.

KOReader forks itself for background work (rendering cache, cover/thumbnail extraction, ...) and ends the
child with os.exit(0), i.e. libc exit(), which runs atexit handlers. On this tablet the Mali GPU driver's
handler calls eglTerminate(), which disconnects the window surface the child shares with the parent over
binder (tombstone: exit -> __cxa_finalize -> libGLES_mali eglTerminate -> Surface::disconnect -> SIGSEGV).
The real KOReader is left with a dead surface; on the next resume createSurface fails and the screen stays
black until KOReader is restarted. KOReader's own source notes it "technically ought to be _exit".

In every subprocess only (after fork), os.exit is replaced by _exit, which skips those handlers.
Delete this file to undo.
--]]

local ffi = require("ffi")
local util = require("ffi/util")

pcall(ffi.cdef, "void _exit(int status);")

util.addRunInSubProcessAfterForkFunc("meebook_safe_exit", function()
    os.exit = function(code)
        io.stdout:flush()
        io.stderr:flush()
        if code == true or code == nil then code = 0 elseif code == false then code = 1 end
        ffi.C._exit(tonumber(code) or 0)
    end
end)
