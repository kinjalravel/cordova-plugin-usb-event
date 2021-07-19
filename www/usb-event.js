var exec = require('cordova/exec');

/**
 * list USB devices.
 */
exports.listDevices = function(success, error, options) {
    exec(success, error, 'UsbEvent', 'listDevices', [options]);
};

/**
 * Check callback is already exists.
 */
exports.existsRegisteredCallback = function(success, error) {
    exec(success, error, 'UsbEvent', 'existsRegisteredCallback', []);
};

/**
 * Register USB attached and detached event callback.
 */
exports.registerEventCallback = function(success, error, options) {
    exec(success, error, 'UsbEvent', 'registerEventCallback', [options]);
};

/**
 * Clear registered callback.
 */
exports.unregisterEventCallback = function(success, error, options) {
    exec(success, error, 'UsbEvent', 'unregisterEventCallback', [options]);
};

/**
 * Create file callback.
 */
exports.createFileEventCallback = function(options, success, error) {
    exec(success, error, 'UsbEvent', 'createFileEventCallback', [options]);
};

/**
 * Delete file callback.
 */
exports.deleteFileEventCallback = function(options, success, error) {
    exec(success, error, 'UsbEvent', 'deleteFileEventCallback', [options]);
};

/**
 * Read file callback.
 */
exports.readFileEventCallback = function(options, success, error) {
    exec(success, error, 'UsbEvent', 'readFileEventCallback', [options]);
};

/**
 * Write file callback.
 */
exports.writeFileEventCallback = function(options, success, error) {
    exec(success, error, 'UsbEvent', 'writeFileEventCallback', [options]);
};

/**
 * check file callback.
 */
exports.fileExistFileEventCallback = function(options, success, error) {
    exec(success, error, 'UsbEvent', 'fileExistFileEventCallback', [options]);
};
