# Changelog

## 1.1.0

### Fixed
- Moving a file no longer silently overwrites a file with the same name in the destination; you can
  choose *Replace* or *Keep both*.
- Moving between storage volumes falls back to copy + verify + delete instead of failing silently.
- Failed deletions are reported and the file stays in the list.
- The delete button and counter only consider the selection of the folder on screen; selections in
  other folders are kept for when you return.
- Search is closed and cleared when you leave the folder (it used to keep filtering invisibly).
- The permission flow re-checks access when you return from system settings.
- Sorting keeps folders on top; size sort orders folders by item count.
- State (folder, selection, sort, move in progress) survives rotation and process death.

### Added
- Undo for deletions, rename, create folder, file details dialog, hidden-file and empty-folder
  filters, breadcrumb navigation, file type badges.
- Redesigned Material 3 UI with light/dark theme; English and Turkish translations.
- Unit tests for file operations, trash, selection and sorting; GitHub Actions CI.

### Changed
- Application ID is now `com.enestopal.quickdeleter` (required for Google Play).
- Target SDK 36, Kotlin 2.1, AGP 8.10, Java 17; release builds are minified and resource-shrunk.
- All file system access moved off the main thread into a ViewModel.
