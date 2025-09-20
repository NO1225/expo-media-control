module.exports = {
  root: true,
  extends: ['universe/native', 'universe/web'],
  ignorePatterns: ['build'],
  rules: {
    // Relax some rules for publication readiness
    '@typescript-eslint/no-unused-vars': 'warn',
    'no-console': 'off',
    '@typescript-eslint/no-explicit-any': 'warn',
  },
};
