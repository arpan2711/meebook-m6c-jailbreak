local _ = require("gettext")
return {
    name = "favsync",
    fullname = _("Save favourites immediately"),
    description = _([[Writes collections (favourites) to disk as soon as they change, instead of when the file browser closes, so the home screen sees new favourites right away.]]),
}
