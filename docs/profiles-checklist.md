# Profiles Feature To-Do

- [x] **Wire up the "Profiles" entry point**  
  - [x] In both `activity_pc_view.xml` and `activity_app_view.xml`, add a new  
    `<ImageButton android:id="@+id/profilesButton" …>` immediately beside the settings cog.  
  - [x] In `PcView.java` and `AppView.java`, add:
  
    ```java
    findViewById(R.id.profilesButton)
        .setOnClickListener(v -> startActivity(new Intent(this, ProfilesActivity.class)));
    ```

- [x] **Create the Profiles list screen (`ProfilesActivity`)**  
  - [x] New `ProfilesActivity extends AppCompatActivity`, declared in `AndroidManifest.xml`.  
  - [x] Layout `res/layout/activity_profiles.xml`: `RecyclerView` + FloatingActionButton ("+").  
  - [x] Row layout `res/layout/row_profile.xml`:  
    - `TextView` for profile name  
    - RadioButton (or CheckView) for "active"  
    - Edit (pencil) icon  
    - Delete (trash) icon  
  - [x] `ProfilesAdapter` backed by `ProfilesManager.getInstance().getProfiles()`:  
    - Clicking name or pencil → launch `EditProfileActivity` (pass UUID or null).  
    - Toggling radio → `ProfilesManager.setActive(uuid)` + `save()`.  
    - Trash → confirm → `ProfilesManager.delete(uuid)` + `save()`.  
  - [x] FAB "+" → `EditProfileActivity` in "new" mode.  
  - [x] Register a `ProfilesManager.ProfileChangeListener` to call `adapter.notifyDataSetChanged()` on data changes.

- [x] **Build the Profile-editor screen (`EditProfileActivity`)**  
  - [x] New `EditProfileActivity extends AppCompatActivity`, in the manifest.  
  - [x] Layout `res/layout/activity_edit_profile.xml`: toolbar with "Save" button + `FrameLayout` container.  
  - [x] Host a `PreferenceFragmentCompat` that:  
    - Creates its own in-memory `OverlaySharedPreferences` over an empty `HashMap<String,Object>`.  
    - Loads all preferences from `R.xml/preferences` using that wrapper.  
    - Highlights changed items by checking `patch.containsKey(key)` after binding each control.  
  - [x] In `onSave`:  
    - Call `OptionDiffUtil.diff(this)` against that in-memory wrapper to get the full patch map.  
    - If new profile: instantiate `SettingsProfile` (UUID, name, createdUtc, modifiedUtc=now, options=patch); else update existing.  
    - `ProfilesManager.add()` or `update()`, then `save()`.  
    - Finish activity.

- [x] **In-UI support for renaming & timestamps**  
  - [x] In `EditProfileActivity` toolbar, show an "Edit name" action or inline `EditText`.  
  - [x] When name changes, update `profile.name` and `profile.modifiedUtc`.  
  - [x] On the list row (`row_profile.xml`), show `modifiedUtc` as a subtitle.

- [x] **Persist & load consistently**  
  - [x] In every `ProfilesManager.add/update/delete/setActive`, call `save(context)` immediately.  
  - [x] On app launch (`MoonlightApplication.onCreate`), ensure `load()` is called.  
  - [x] Ensure real-prefs reads use the overlay wrapper.

- [x] **Highlighting & UX polish**  
  - [x] Define a `@color/profileAccent` in `res/values/colors.xml`.  
  - [x] After `addPreferencesFromResource`, traverse all prefs—if `patch.containsKey(key)`, tint the title or add accent.  
  - [x] Show a Snackbar on save: "Profile 'Foo' saved.".  
  - [x] If the active profile changes, show a Toast: "Activated profile 'Foo.'".

- [x] **Clean up & edge cases**  
  - [x] Deleting the active profile → automatically call `setActive(null)` and `save()`.  
  - [x] If no profiles exist, `ProfilesActivity` should show an empty-state message.  
  - [x] Disallow blank-name profiles.  
  - [x] Truncate long names in the list row.

- [x] **Testing & QA**
  - [x] Manual flows: create → edit values → save → confirm saved JSON + overlay works in fresh session → switch profile → confirm new overlay.
  - [x] Delete & rename.
  - [x] Added unit-tests covering:
    1. `OverlaySharedPreferences` numeric coercion (Double→int/long) – regression for crash fixed on 2025-06-29.
    2. `OptionDiffUtil.diff()` against XML defaults (already in place).

> **Note (2025-06-29):** During real-device testing a startup crash (`java.lang.Double cannot be cast to java.lang.Integer`) revealed that Gson deserializes numeric literals in profile JSON as `Double`, while our overlay code assumed `Integer`/`Long`.  This wasn't caught by existing tests because they manually constructed `Map<String,Object>` with Java literals (Integers).  The bug is fixed and dedicated tests now assert that Double values are correctly coerced via `Number.intValue()/longValue()`.

## Tips & References

- Icon: add `ic_profiles` drawable to `app/src/main/res/drawable/ic_profiles.png` and reference via `@drawable/ic_profiles`.
- ProfilesActivity stub: create `ProfilesActivity.java` under `app/src/main/java/com/limelight/profiles`, extend `AppCompatActivity`, import `android.content.Intent`, and declare in `AndroidManifest.xml`.
- Layout files:
  - `activity_profiles.xml`: include `<RecyclerView android:id="@+id/profilesRecyclerView" .../>` and `<FloatingActionButton android:id="@+id/addProfileFab" .../>`.
  - `row_profile.xml`: define `<TextView android:id="@+id/profileName" .../>`, `<RadioButton android:id="@+id/profileActive" .../>`, `<ImageButton android:id="@+id/editProfile" .../>`, `<ImageButton android:id="@+id/deleteProfile" .../>`.
- Adapter: implement `ProfilesAdapter` in `com.limelight.profiles`, backed by `ProfilesManager.getInstance().getProfiles()`.
- ProfilesManager: see `app/src/main/java/com/limelight/profiles/ProfilesManager.java` for load/save and `getOverlayingSharedPreferences`.
- OptionDiffUtil: use `OptionDiffUtil.diff(Context)` in `app/src/main/java/com/limelight/profiles/OptionDiffUtil.java` to generate the options map.
- Color: define `<color name="profileAccent">#FF4081</color>` in `app/src/main/res/values/colors.xml`.
- Application init: in `MoonlightApplication.onCreate()`, call `ProfilesManager.getInstance().load(this)` to load profiles on startup.
