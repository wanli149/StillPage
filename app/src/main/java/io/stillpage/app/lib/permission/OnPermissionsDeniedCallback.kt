package io.stillpage.app.lib.permission

interface OnPermissionsDeniedCallback {

    fun onPermissionsDenied(deniedPermissions: Array<String>)

}
