package sasd97.github.com.translator.ui.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import sasd97.github.com.translator.R;
import sasd97.github.com.translator.models.TranslationModel;
import sasd97.github.com.translator.services.HistorySqlService;
import sasd97.github.com.translator.ui.base.BaseViewHolder;

/**
 * Created by alexander on 10/04/2017.
 */

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    public interface HistoryInteractionListener {
        void onRemoveFavorite(int position);
        void onDelete(int position);
    }

    private List<TranslationModel> translations;
    private HistoryInteractionListener historyInteractionListener;

    public HistoryAdapter() {
        translations = new ArrayList<>();
    }

    public HistoryAdapter(@NonNull List<TranslationModel> translations) {
        this.translations = translations;
    }

    public void setHistoryInteractionListener(HistoryInteractionListener historyInteractionListener) {
        this.historyInteractionListener = historyInteractionListener;
    }

    public class HistoryViewHolder extends BaseViewHolder implements View.OnClickListener {

        @BindView(R.id.favorite_holder) View favoriteView;
        @BindView(R.id.favorite_image) ImageView favoriteImageView;
        @BindView(R.id.history_original_text) TextView originalTextView;
        @BindView(R.id.history_translated_text) TextView translatedTextView;

        public HistoryViewHolder(View itemView) {
            super(itemView);
        }

        @Override
        protected void setupViews() {
            favoriteView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            TranslationModel translation = translations.get(getAdapterPosition());
            translation.switchFavorite();
            changeFavoriteIcon(translation);

            HistorySqlService.update(translation);

            if (historyInteractionListener == null) return;
            if (!translation.isFavorite()) historyInteractionListener.onRemoveFavorite(getAdapterPosition());
        }

        private void changeFavoriteIcon(TranslationModel translation) {
            if (translation.isFavorite()) favoriteImageView.setImageResource(R.drawable.ic_favorite_black_24dp);
            else favoriteImageView.setImageResource(R.drawable.ic_favorite_border_black_24dp);
        }
    }

    @Override
    public HistoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View v = LayoutInflater.from(context).inflate(R.layout.row_history, parent, false);
        return new HistoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(HistoryViewHolder holder, int position) {
        TranslationModel translation = translations.get(position);

        holder.originalTextView.setText(translation.getOriginalText());
        holder.translatedTextView.setText(translation.getTranslatedText());

        if (translation.isFavorite()) holder.favoriteImageView.setImageResource(R.drawable.ic_favorite_black_24dp);
        else holder.favoriteImageView.setImageResource(R.drawable.ic_favorite_border_black_24dp);
    }

    public void addHistories(List<TranslationModel> translations) {
        int size = this.translations.size();
        this.translations.addAll(translations);
        notifyItemRangeInserted(size, this.translations.size());
    }

    public void removeHistory(int position) {
        translations.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public int getItemCount() {
        return translations.size();
    }
}