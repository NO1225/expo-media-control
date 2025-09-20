# Contributing to expo-media-control

We love your input! We want to make contributing to expo-media-control as easy and transparent as possible, whether it's:

- Reporting a bug
- Discussing the current state of the code
- Submitting a fix
- Proposing new features
- Becoming a maintainer

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Development Process](#development-process)
- [Setting Up Development Environment](#setting-up-development-environment)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Bug Reports](#bug-reports)
- [Feature Requests](#feature-requests)
- [Code Style](#code-style)
- [Testing](#testing)
- [License](#license)

## Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to [safartyjumah@gmail.com](mailto:safartyjumah@gmail.com).

### Our Pledge

We pledge to make participation in our project a harassment-free experience for everyone, regardless of age, body size, disability, ethnicity, gender identity and expression, level of experience, nationality, personal appearance, race, religion, or sexual identity and orientation.

## Development Process

We use GitHub to host code, track issues and feature requests, as well as accept pull requests.

1. Fork the repo and create your branch from `main`
2. If you've added code that should be tested, add tests
3. If you've changed APIs, update the documentation
4. Ensure the test suite passes
5. Make sure your code lints
6. Issue that pull request!

## Setting Up Development Environment

### Prerequisites

- Node.js 16+ and npm/yarn/pnpm
- React Native development environment
- Android Studio (for Android development)
- Xcode 14+ (for iOS development, macOS only)
- Expo CLI

### Installation

1. **Clone your fork:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/expo-media-control.git
   cd expo-media-control
   ```

2. **Install dependencies:**
   ```bash
   npm install
   # or
   yarn install
   ```

3. **Build the module:**
   ```bash
   npm run build
   ```

4. **Set up example app:**
   ```bash
   cd example
   npm install
   ```

### Running Example App

```bash
# Start Metro bundler
cd example
npm start

# Run on iOS
npm run ios

# Run on Android  
npm run android
```

## How to Contribute

### 🐛 Reporting Bugs

Bugs are tracked as GitHub issues. Create an issue and provide the following information:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Provide specific examples to demonstrate the steps**
- **Describe the behavior you observed** and **what behavior you expected**
- **Include platform information** (iOS/Android version, React Native version, Expo SDK version)
- **Include relevant code snippets or logs**

**Bug Report Template:**
```markdown
## Bug Description
A clear description of what the bug is.

## Steps to Reproduce
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

## Expected Behavior
What you expected to happen.

## Actual Behavior  
What actually happened.

## Environment
- expo-media-control version: 
- React Native version:
- Expo SDK version:
- Platform: iOS/Android
- Device: 
- OS version:

## Additional Context
Any other context about the problem.
```

### 🚀 Feature Requests

Feature requests are welcome! Please provide:

- **Clear use case** - Explain why this feature would be useful
- **Detailed description** - How should it work?
- **API proposal** - If applicable, suggest how the API should look
- **Platform considerations** - Any iOS/Android specific requirements

### 🔧 Code Contributions

1. **Choose an issue** - Look for issues labeled `good first issue` or `help wanted`
2. **Fork and clone** the repository
3. **Create a branch** - Use a descriptive name like `fix-ios-artwork-loading`
4. **Make your changes** - Follow the code style guidelines
5. **Add tests** if applicable
6. **Update documentation** if you changed any APIs
7. **Submit a pull request**

## Pull Request Process

### Before Submitting

- [ ] Code follows the style guidelines
- [ ] Self-review of the code
- [ ] Code is commented, particularly in hard-to-understand areas
- [ ] Documentation updated if needed
- [ ] Tests added for new features/fixes
- [ ] All tests pass
- [ ] No merge conflicts

### PR Template

```markdown
## Description
Brief description of changes.

## Type of Change
- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that causes existing functionality to change)
- [ ] Documentation update

## Testing
- [ ] Tested on iOS
- [ ] Tested on Android  
- [ ] Added/updated tests
- [ ] Example app updated if needed

## Screenshots
If applicable, add screenshots.

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-reviewed code
- [ ] Commented hard-to-understand areas
- [ ] Updated documentation
- [ ] Added tests
- [ ] Tests pass
- [ ] No merge conflicts
```

### Review Process

1. **Automated checks** must pass (linting, building, tests)
2. **Code review** by maintainers
3. **Testing** on both platforms if applicable  
4. **Approval** from at least one maintainer
5. **Merge** by maintainer

## Code Style

### TypeScript/JavaScript

- Use TypeScript for all new code
- Use Prettier for formatting
- Use ESLint for linting
- Prefer `const` over `let`
- Use descriptive variable names
- Add JSDoc comments for public APIs

```typescript
/**
 * Updates the media metadata displayed in system controls
 * @param metadata The media metadata to display
 * @throws {Error} When metadata is invalid
 */
async updateMetadata(metadata: MediaMetadata): Promise<void> {
  // Implementation
}
```

### Native Code (iOS/Android)

**Swift (iOS):**
- Use Swift 5.9+ features
- Follow Swift API Design Guidelines
- Use proper memory management (weak references)
- Handle errors appropriately
- Add comprehensive documentation

**Kotlin (Android):**  
- Use Kotlin coroutines appropriately
- Follow Android development best practices
- Handle memory leaks prevention
- Use proper lifecycle management
- Thread-safe operations

### Commit Messages

Use conventional commits format:

```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Adding/updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(ios): add CarPlay support
fix(android): resolve memory leak in artwork loading
docs: update API documentation for new features
```

## Testing

### Running Tests

```bash
# Run all tests
npm test

# Run TypeScript tests
npm run test:ts

# Run linting
npm run lint

# Run type checking
npm run type-check
```

### Writing Tests

- Add unit tests for new functions
- Add integration tests for complex features
- Test both iOS and Android when applicable
- Mock external dependencies appropriately

### Manual Testing

Test on real devices when possible:

1. **Basic functionality** - All core features work
2. **Background scenarios** - App backgrounding/foregrounding
3. **Interruption handling** - Phone calls, notifications
4. **Memory usage** - Extended use scenarios
5. **Different media types** - Various audio formats and sources

## Release Process

Releases are handled by maintainers:

1. Version bump following semver
2. Update CHANGELOG.md
3. Create GitHub release
4. Publish to npm
5. Update documentation

## Getting Help

- **GitHub Discussions** - For questions and general discussion
- **GitHub Issues** - For bugs and feature requests
- **Discord** - For real-time community support (link in README)

## Recognition

Contributors will be:

- Added to CONTRIBUTORS.md
- Mentioned in release notes for significant contributions
- Given appropriate GitHub repository permissions for regular contributors

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

## Quick Links

- [Issues](https://github.com/NO1225/expo-media-control/issues)
- [Pull Requests](https://github.com/NO1225/expo-media-control/pulls)
- [Discussions](https://github.com/NO1225/expo-media-control/discussions)
- [Project Board](https://github.com/NO1225/expo-media-control/projects)

Thank you for contributing! 🎉