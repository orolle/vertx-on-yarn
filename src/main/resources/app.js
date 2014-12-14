var container = require('vertx/container');
var logger = container.logger;
var config = container.config;

// Logging not working
// Seems: Logging not forwarded to twill logger -> needs more investigation!
// Maybe: verticle not deployed successfully, but PlatformManager says otherwise.
logger.info('config is ' + JSON.stringify(config));
