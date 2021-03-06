/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.evergreen_ils.searchCatalog;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.*;
import android.widget.TextView;
import com.android.volley.toolbox.NetworkImageView;

import org.evergreen_ils.net.Gateway;
import org.evergreen_ils.net.Volley;
import org.evergreen_ils.R;
import org.evergreen_ils.android.Log;

import java.util.List;

/**
 * Provide views to RecyclerView with data from records.
 */
public class RecordViewAdapter extends RecyclerView.Adapter<RecordViewAdapter.ViewHolder> {
    private static final String TAG = RecordViewAdapter.class.getSimpleName();

    private List<RecordInfo> records;

    /**
     * Provide a reference to the type of views that you are using (custom ViewHolder)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final NetworkImageView imageView;
        private final TextView titleText;
        private final TextView authorText;
        private final TextView iconFormatText;
        private final TextView publisherText;
        private Integer bound_record_id;

        public ViewHolder(View v) {
            super(v);
            imageView = (NetworkImageView) v.findViewById(R.id.search_record_img);
            titleText = (TextView) v.findViewById(R.id.search_record_title);
            authorText = (TextView) v.findViewById(R.id.search_record_author);
            iconFormatText = (TextView) v.findViewById(R.id.search_record_format);
            publisherText = (TextView) v.findViewById(R.id.search_record_publishing);
        }

        public void bindView(final RecordInfo record) {
            bound_record_id = record.doc_id;
            Log.d(TAG, record.doc_id + ": bindView");
            final Context context = imageView.getContext();
            // todo is it better to load /jacket/medium/ here so it is cached for the details view?
            final String url = Gateway.INSTANCE.getUrl("/opac/extras/ac/jacket/small/r/" + record.doc_id);
            imageView.setImageUrl(url, Volley.getInstance(context).getImageLoader());
            //imageView.setDefaultImageResId(R.drawable.missing_art);//for screenshots
            titleText.setText((record.title != null) ? record.title : "");
            RecordLoader.fetchSummaryMetadata(record, context, new RecordLoader.ResponseListener() {
                @Override
                public void onMetadataLoaded() {
                    titleText.setText(record.title);
                    authorText.setText(record.author);
                    publisherText.setText(record.getPublishingInfo());
                }

                @Override
                public void onIconFormatLoaded() {
                    iconFormatText.setText(record.getIconFormatLabel());
                }
            });
        }
    }


    // Initialize the dataset of the Adapter.
    public RecordViewAdapter(List<RecordInfo> dataSet) {
        records = dataSet;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view.
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.search_result_item, viewGroup, false);

        return new ViewHolder(v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final int position) {
        final RecordInfo record = records.get(position);
        viewHolder.bindView(record);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return records.size();
    }
}
