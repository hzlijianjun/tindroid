package co.tinode.tindroid;

import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Activity holds the following fragments:
 *   ContactsFragment
 *     ChatListFragment archive=false
 *     ContactListFragment
 *   ChatListFragment archive=true
 *   AccountInfoFragment
 *
 * This activity owns 'me' topic.
 */
public class ContactsActivity extends AppCompatActivity implements
        ContactListFragment.OnContactsInteractionListener {

    private static final String TAG = "ContactsActivity";

    static final String FRAGMENT_CONTACTS = "contacts";
    static final String FRAGMENT_EDIT_ACCOUNT = "edit_account";
    static final String FRAGMENT_ARCHIVE = "archive";

    private MeListener mMeTopicListener = null;
    private MeTopic mMeTopic = null;

    static {
        // Otherwise crash on pre-Lollipop (per-API 21)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_contacts);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.contentFragment, new ContactsFragment(), FRAGMENT_CONTACTS)
                .commit();

        mMeTopic = Cache.getTinode().getMeTopic();
        mMeTopicListener = new MeListener();
    }

    /**
     * This interface callback lets the main contacts list fragment notify
     * this activity that a contact has been selected.
     *
     * @param contactUri The contact Uri to the selected contact.
     */
    @Override
    public void onContactSelected(Uri contactUri) {
        // Otherwise single pane layout, start a new ContactDetailActivity with
        // the contact Uri
        //Intent intent = new Intent(this, ContactDetailActivity.class);
        //intent.setData(contactUri);
        //startActivity(intent);
    }

    /**
     * This interface callback lets the main contacts list fragment notify
     * this activity that a contact is no longer selected.
     */
    @Override
    public void onSelectionCleared() {
    }

    /**
     * onResume restores subscription to 'me' topic and sets listener.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onResume() {
        super.onResume();

        final Tinode tinode = Cache.getTinode();

        tinode.setListener(new ContactsEventListener(tinode.isConnected()));

        UiUtils.setupToolbar(this, null, null, false);

        // This will issue a subscription request.
        UiUtils.attachMeTopic(this, mMeTopicListener);
    }

    private void datasetChanged() {
        final FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(FRAGMENT_ARCHIVE);
        if (fragment != null && fragment.isVisible()) {
            ((ChatListFragment) fragment).datasetChanged();
        }
        fragment = fm.findFragmentByTag(FRAGMENT_CONTACTS);
        if (fragment != null && fragment.isVisible()) {
            ((ContactsFragment) fragment).chatDatasetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Cache.getTinode().setListener(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onStop() {
        super.onStop();
        if (mMeTopic != null) {
            mMeTopic.setListener(null);
        }
    }

    //protected ChatListAdapter getChatListAdapter() {
    //    return mChatListAdapter;
    //}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Enable options menu by returning true
        return true;
    }

    void showFragment(String tag) {
        final FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);

        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_EDIT_ACCOUNT:
                    fragment = new AccountInfoFragment();
                    break;
                case FRAGMENT_ARCHIVE:
                    fragment = new ChatListFragment();
                    Bundle args = new Bundle();
                    args.putBoolean("archive", Boolean.TRUE);
                    fragment.setArguments(args);
                    break;
                case FRAGMENT_CONTACTS:
                    fragment = new ContactsFragment();
                    break;
                default:
                    throw new IllegalArgumentException("Failed to create fragment: unknown tag "+tag);
            }
        }

        FragmentTransaction trx = fm.beginTransaction();
        trx.add(R.id.contentFragment, fragment, tag);
        trx.addToBackStack(tag)
                .show(fragment)
                .commit();
    }

    public void selectTab(final int pageIndex) {
        FragmentManager fm = getSupportFragmentManager();
        ContactsFragment contacts = (ContactsFragment) fm.findFragmentByTag(FRAGMENT_CONTACTS);
        if (contacts == null) {
            return;
        }

        contacts.selectTab(pageIndex);
    }

    private class MeListener extends MeTopic.MeListener<VxCard> {

        @Override
        public void onInfo(MsgServerInfo info) {
            // FIXME: handle {info}
            Log.d(TAG, "Contacts got onInfo update '" + info.what + "'");
        }

        @Override
        public void onPres(MsgServerPres pres) {
            if ("msg".equals(pres.what)) {
                datasetChanged();
            } else if ("off".equals(pres.what) || "on".equals(pres.what)) {
                datasetChanged();
            }
        }

        @Override
        public void onMetaSub(final Subscription<VxCard,PrivateType> sub) {
            if (sub.pub != null) {
                sub.pub.constructBitmap();
            }
        }

        @Override
        public void onMetaDesc(final Description<VxCard,PrivateType> desc) {
            if (desc.pub != null) {
                desc.pub.constructBitmap();
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AccountInfoFragment fragment = (AccountInfoFragment) getSupportFragmentManager().
                            findFragmentByTag(FRAGMENT_EDIT_ACCOUNT);
                    if (fragment != null && fragment.isVisible()) {
                        fragment.updateFormValues(ContactsActivity.this, mMeTopic);
                    }
                }
            });
        }

        @Override
        public void onSubsUpdated() {
            datasetChanged();
        }

        @Override
        public void onContUpdate(final Subscription<VxCard,PrivateType> sub) {
            // Method makes no sense in context of MeTopic.
            throw new UnsupportedOperationException();
        }
    }

    private class ContactsEventListener extends UiUtils.EventListener {
        ContactsEventListener(boolean online) {
            super(ContactsActivity.this, online);
        }

        @Override
        public void onLogin(int code, String txt) {
            super.onLogin(code, txt);
            UiUtils.attachMeTopic(ContactsActivity.this, mMeTopicListener);
        }
    }
}