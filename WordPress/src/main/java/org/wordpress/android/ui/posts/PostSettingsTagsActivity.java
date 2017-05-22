package org.wordpress.android.ui.posts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.model.PostModel;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.model.TermModel;
import org.wordpress.android.fluxc.store.PostStore;
import org.wordpress.android.fluxc.store.TaxonomyStore;
import org.wordpress.android.util.ToastUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class PostSettingsTagsActivity extends AppCompatActivity implements TextWatcher {
    public static final String KEY_LOCAL_POST_ID = "KEY_POST_ID";
    private SiteModel mSite;
    private PostModel mPost;

    private EditText mTagsEditText;
    private TagsRecyclerViewAdapter mAdapter;

    @Inject Dispatcher mDispatcher;
    @Inject TaxonomyStore mTaxonomyStore;
    @Inject PostStore mPostStore;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getApplicationContext()).component().inject(this);

        int localPostId;
        if (savedInstanceState == null) {
            mSite = (SiteModel) getIntent().getSerializableExtra(WordPress.SITE);
            localPostId = getIntent().getIntExtra(KEY_LOCAL_POST_ID, 0);
        } else {
            mSite = (SiteModel) savedInstanceState.getSerializable(WordPress.SITE);
            localPostId = savedInstanceState.getInt(KEY_LOCAL_POST_ID, 0);
        }
        mPost = mPostStore.getPostByLocalPostId(localPostId);
        if (mSite == null) {
            ToastUtils.showToast(this, R.string.blog_not_found, ToastUtils.Duration.SHORT);
            finish();
            return;
        }

        setContentView(R.layout.post_settings_tags_fragment);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mTagsEditText = (EditText) findViewById(R.id.tags_edit_text);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.tags_suggestion_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mAdapter = new TagsRecyclerViewAdapter(this);
        mAdapter.setAllTags(mTaxonomyStore.getTagsForPost(mPost, mSite));
        recyclerView.setAdapter(mAdapter);

        if (mTagsEditText != null) {
            mTagsEditText.addTextChangedListener(this);
            String tags = TextUtils.join(",", mPost.getTagNameList());
            if (!tags.equals("")) {
                // add a , at the end so the user can start typing a new tag
                tags += ",";
                mTagsEditText.setText(tags);
                mTagsEditText.setSelection(mTagsEditText.length());
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mDispatcher.register(this);
    }

    @Override
    public void onStop() {
        mDispatcher.unregister(this);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(WordPress.SITE, mSite);
        outState.putInt(KEY_LOCAL_POST_ID, mPost.getId());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            saveAndFinish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
        super.onBackPressed();
    }

    private void saveAndFinish() {
        Bundle bundle = new Bundle();
        Intent intent = new Intent();
        intent.putExtras(bundle);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        // No-op
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        filterListForCurrentText();
    }

    @Override
    public void afterTextChanged(Editable editable) {
        // No-op
    }

    // Find the text after the last occurrence of "," and filter with it
    private void filterListForCurrentText() {
        String text = mTagsEditText.getText().toString();
        if (!TextUtils.isEmpty(text)) {
            int endIndex = text.lastIndexOf(",");
            if (endIndex == -1) {
                mAdapter.filter(text);
            } else if (endIndex + 1 <= text.length()) {
                String textToFilter = text.substring(endIndex + 1);
                mAdapter.filter(textToFilter);
            }
        }
    }

    private void onTagSelected(@NonNull String selectedTag){
        String text = mTagsEditText.getText().toString();
        String updatedText;
        int endIndex = text.lastIndexOf(",");
        if (endIndex == -1) {
            // no "," found, replace the current text with the selectedTag
            updatedText = selectedTag;
        } else {
            // there are multiple tags already, only update the text after the last ","
            updatedText = text.substring(0, endIndex + 1) + selectedTag;
        }
        updatedText += ",";
        mTagsEditText.setText(updatedText);
        mTagsEditText.setSelection(mTagsEditText.length());
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTaxonomyChanged(TaxonomyStore.OnTaxonomyChanged event) {
        switch (event.causeOfChange) {
            case FETCH_TAGS:
                mAdapter.setAllTags(mTaxonomyStore.getTagsForPost(mPost, mSite));
                filterListForCurrentText();
                break;
        }
    }

    private class TagsRecyclerViewAdapter extends RecyclerView.Adapter<TagsRecyclerViewAdapter.TagViewHolder> {
        private List<TermModel> mAllTags;
        private List<TermModel> mFilteredTags;
        private Context mContext;

        TagsRecyclerViewAdapter(Context context) {
            mContext = context;
            mFilteredTags = new ArrayList<>();
        }

        @Override
        public TagViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.tags_list_row, parent, false);
            return new TagViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final TagViewHolder holder, int position) {
            holder.nameTextView.setText(mFilteredTags.get(position).getName());
        }

        @Override
        public int getItemCount() {
            return mFilteredTags.size();
        }

        void setAllTags(List<TermModel> allTags) {
            mAllTags = allTags;
        }

        public void filter(final String text) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mFilteredTags.clear();
                    if (TextUtils.isEmpty(text)) {
                        mFilteredTags.addAll(mAllTags);
                    } else {
                        for (TermModel tag : mAllTags) {
                            if (tag.getName().toLowerCase().contains(text.toLowerCase())) {
                                mFilteredTags.add(tag);
                            }
                        }
                    }

                    ((Activity) mContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            notifyDataSetChanged();
                        }
                    });
                }
            }).start();

        }

        class TagViewHolder extends RecyclerView.ViewHolder {
            private final TextView nameTextView;

            TagViewHolder(View view) {
                super(view);
                nameTextView = (TextView) view.findViewById(R.id.tag_name);
                LinearLayout linearLayout = (LinearLayout) view.findViewById(R.id.tags_list_row_container);
                linearLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        onTagSelected(nameTextView.getText().toString());
                    }
                });
            }
        }
    }
}
