// Learn more https://docs.expo.dev/guides/monorepos/
// Expo's default Metro config detects npm workspaces, so the packages in
// ../../packages resolve to their sources without extra configuration.
const { getDefaultConfig } = require('expo/metro-config');

module.exports = getDefaultConfig(__dirname);
