package ca.tetchel.shexter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.List;

/**
 * Data type class to hold info about a contact, and access preferred contact data when it's
 * required.
 */
class Contact {
    private static final String TAG = Contact.class.getSimpleName();

    private String name;
    // numbers includes the type, as returned from getNumberForContact
    private List<String> numbers;

    Contact(String name, List<String> numbers) {
        this.name = name;
        this.numbers = numbers;
    }

    /**
     * Set the preferred number to the given one.
     *
     * @param number Number to set the contact's preferred number to.
     */
    private void setPreferred(String number) {
        boolean contains = false;
        for (String n : numbers) {
            if (numbers.size() == 1 || n.contains(number)) {
                contains = true;
                number = n;
            }
        }
        if (!contains) {
            // should not happen
            Log.e(TAG, "Invalid number " + number + " passed to setPreferred!!");
            return;
        }
        Context c = SmsServerService.instance().getApplicationContext();
        SharedPreferences sp = c.getSharedPreferences(c.getString(R.string.preferred_contacts_file),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sp.edit();
        edit.putString(c.getString(R.string.preferred_contacts_prefix) + name, number);
        Log.d(TAG, "Finished setting " + name + " pref to " + number);
        edit.apply();
    }

    /**
     * Used to set preferred using an index instead of a string number. Intended to be used when
     * getting setpref (NOT list) request from user.
     *
     * @param index Which index in numbers to set the new preferred to.
     */
    void setPreferred(int index) {
        Log.d(TAG, "Setting " + name() + "'s preferred to " + numbers().get(index));
        setPreferred(numbers.get(index));
    }

    /**
     * Check the Shared Preferences for a preferred number.
     * ***** Could also check if the number is still in the phone book, and if not,
     * require a setpref request.
     *
     * @return The preferred number if it is in the preferences, null otherwise.
     */
    private String checkPrefs() {
        Context c = SmsServerService.instance().getApplicationContext();
        SharedPreferences prefs = c.getSharedPreferences(
                c.getString(R.string.preferred_contacts_file),
                Context.MODE_PRIVATE);
        return prefs.getString(c.getString(R.string.preferred_contacts_prefix) + name, null);
    }

    // Getters //
    boolean hasPreferred() {
        return checkPrefs() != null;
    }

    String preferred() {
        return checkPrefs();
    }

    String name() {
        return name;
    }

    List<String> numbers() {
        return numbers;
    }

    int count() {
        return numbers.size();
    }
}