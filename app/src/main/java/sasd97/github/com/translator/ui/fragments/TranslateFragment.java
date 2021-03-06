package sasd97.github.com.translator.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.TextInputEditText;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationSet;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindArray;
import butterknife.BindColor;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.OnClick;
import retrofit2.Call;
import sasd97.github.com.translator.R;
import sasd97.github.com.translator.events.OnTranslationChangedListener;
import sasd97.github.com.translator.http.HttpError;
import sasd97.github.com.translator.http.HttpResultListener;
import sasd97.github.com.translator.models.Dictionary.DefinitionDictionaryModel;
import sasd97.github.com.translator.models.Dictionary.DictionaryModel;
import sasd97.github.com.translator.models.Dictionary.TranslationDictionaryModel;
import sasd97.github.com.translator.models.TranslationModel;
import sasd97.github.com.translator.repositories.LanguageRepository;
import sasd97.github.com.translator.repositories.TranslationRepository;
import sasd97.github.com.translator.services.HistorySqlService;
import sasd97.github.com.translator.ui.adapters.AlternativeTranslationAdapter;
import sasd97.github.com.translator.ui.base.BaseFragment;
import sasd97.github.com.translator.utils.AnimationUtils;
import sasd97.github.com.translator.utils.ShareUtils;
import sasd97.github.com.translator.utils.SpinnerUtils;
import sasd97.github.com.translator.utils.watchers.ClearButtonAppearanceDetector;
import sasd97.github.com.translator.utils.watchers.StopTypingDetector;

import static sasd97.github.com.translator.constants.ViewConstants.ALTERNATIVE_TRANSLATION_VIEW_HEIGHT;
import static sasd97.github.com.translator.constants.ViewConstants.SPACE;
import static sasd97.github.com.translator.constants.ViewConstants.TRANSLATION_LIMIT;
import static sasd97.github.com.translator.http.YandexAPIWrapper.lookup;
import static sasd97.github.com.translator.http.YandexAPIWrapper.translate;

public class TranslateFragment extends BaseFragment
        implements AdapterView.OnItemSelectedListener,
        StopTypingDetector.TypingListener,
        ClearButtonAppearanceDetector.ClearButtonAppearanceListener {

    //region variables

    private static final String TAG = TranslateFragment.class.getCanonicalName();
    private static final String TRANSLATION_ARG = "TRANSLATION_ARGUMENT";

    private Handler handler;
    private Call<?> activeQuery;

    private String[] targetLanguagesList;
    private StopTypingDetector stopTypingDetector;
    private OnTranslationChangedListener listener;
    private AlternativeTranslationAdapter alternativeTranslationAdapter;

    private LanguageRepository languageRepository = new LanguageRepository();
    private TranslationRepository translationRepository = new TranslationRepository();

    @BindString(R.string.all_automatic_language)
    String automaticLanguageRecognitionString;
    @BindArray(R.array.all_languages)
    String[] allAvailableLanguagesList;
    @BindColor(R.color.colorGreyDark)
    int greyColor;
    @BindColor(R.color.colorRed)
    int redColor;

    @BindView(R.id.spinner)
    View spinner;
    @BindView(R.id.translate_action_favorite)
    ImageView favoritesActionImageView;
    @BindView(R.id.translate_target_language_spinner)
    Spinner targetLanguageSpinner;
    @BindView(R.id.translate_destination_language_spinner)
    Spinner destinationLanguageSpinner;
    @BindView(R.id.translate_input_edittext)
    TextInputEditText translateInputEditText;
    @BindView(R.id.translate_symbol_counter_textview)
    TextView symbolCounterTextView;
    @BindView(R.id.translate_lanugage_holder_textview)
    TextView translationLanguageHolderTitleTextView;
    @BindView(R.id.translate_primary_translation_textview)
    TextView primaryTranslationTextView;
    @BindView(R.id.translate_scrollview)
    NestedScrollView translateScrollView;
    @BindView(R.id.translate_clear_button)
    View clearTranslateView;
    @BindView(R.id.translate_alternative_translation_cardview)
    View alternativeTranslationCardView;
    @BindView(R.id.translate_alternative_translation_recyclerview)
    RecyclerView alternativeTranslationRecyclerView;

    //endregion

    //region fabric methods

    public static TranslateFragment getInstance(TranslationRepository translationRepository) {
        TranslateFragment translateFragment = new TranslateFragment();

        if (translationRepository != null && !translationRepository.isEmpty()) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(TRANSLATION_ARG, translationRepository);
            translateFragment.setArguments(bundle);
        }

        return translateFragment;
    }

    //endregion

    //region initialization

    @Override
    protected int getLayout() {
        return R.layout.fragment_translate;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            setTranslationChangedListener((OnTranslationChangedListener) getActivity());
        } catch (ClassCastException classCastException) {
            classCastException.printStackTrace();
        }
    }

    @Override
    protected void onViewCreated(Bundle state) {
        super.onViewCreated(state);

        handler = new Handler();
        stopTypingDetector = new StopTypingDetector(handler, this);
        translateInputEditText.addTextChangedListener(stopTypingDetector);
        translateInputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                symbolCounterTextView.setText(getString(R.string.translate_format_counter, editable.length()));
                validateForm();
            }
        });
        translateInputEditText.addTextChangedListener(new ClearButtonAppearanceDetector(this));

        onLanguagesInit();

        ArrayAdapter<CharSequence> fromAdapter = new ArrayAdapter<CharSequence>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, targetLanguagesList);
        fromAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetLanguageSpinner.setAdapter(fromAdapter);
        targetLanguageSpinner.setOnItemSelectedListener(this);

        ArrayAdapter<CharSequence> toAdapter = new ArrayAdapter<CharSequence>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, allAvailableLanguagesList);
        toAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        destinationLanguageSpinner.setAdapter(toAdapter);
        destinationLanguageSpinner.setOnItemSelectedListener(this);

        alternativeTranslationAdapter = new AlternativeTranslationAdapter();
        alternativeTranslationRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        alternativeTranslationRecyclerView.setAdapter(alternativeTranslationAdapter);
        alternativeTranslationRecyclerView.setHasFixedSize(true);
        alternativeTranslationRecyclerView.setNestedScrollingEnabled(false);

        if (getArguments() != null) onArgsExists(getArguments());
        else onPrefsInit();
    }

    private void onLanguagesInit() {
        targetLanguagesList = new String[allAvailableLanguagesList.length + 1];
        targetLanguagesList[0] = automaticLanguageRecognitionString;
        System.arraycopy(allAvailableLanguagesList, 0, targetLanguagesList, 1, allAvailableLanguagesList.length);
    }

    private void onPrefsInit() {
        if (languageRepository.restoreTargetLanguage())
            SpinnerUtils.setSpinnerSelection(
                    targetLanguageSpinner,
                    languageRepository.obtainTargetIndex(targetLanguagesList)
            );

        if (languageRepository.restoreDestinationLanguage())
            SpinnerUtils.setSpinnerSelection(
                    destinationLanguageSpinner,
                    languageRepository.obtainDestinationIndex(allAvailableLanguagesList)
            );
    }

    private void onArgsExists(Bundle args) {
        translationRepository = args.getParcelable(TRANSLATION_ARG);
        if (translationRepository == null) return;

        languageRepository = LanguageRepository.fromTranslation(translationRepository.getTranslation());
        stopTypingDetector.setDetectorActive(false);

        SpinnerUtils.setSpinnerSelection(
                targetLanguageSpinner,
                languageRepository.obtainTargetIndex(targetLanguagesList)
        );

        SpinnerUtils.setSpinnerSelection(
                destinationLanguageSpinner,
                languageRepository.obtainDestinationIndex(allAvailableLanguagesList)
        );

        translateInputEditText.setText(translationRepository.getTranslation().getOriginalText());

        if (translationRepository.getDictionary() == null) {
            if(!isDictionary(translationRepository.getTranslation())) {
                setupTranslationView();
            } else {
                showSpinner();
                loadDictionary();
            }
        } else {
            setupTranslationAndDictionaryViews();
        }

        stopTypingDetector.setDetectorActive(true);
    }

    //endregion

    //region setters & getters

    public void setTranslationChangedListener(OnTranslationChangedListener listener) {
        this.listener = listener;
    }

    //endregion

    //region views animation

    private AnimationSet showTranslationViews(boolean isShowingBoth) {
        AnimationUtils.fadeOut(spinner);
        if (isShowingBoth) alternativeTranslationCardView.setVisibility(View.VISIBLE);
        else alternativeTranslationCardView.setVisibility(View.GONE);
        primaryTranslationTextView.setVisibility(View.VISIBLE);
        return AnimationUtils.fadeIn(translateScrollView);
    }

    private void showSpinner() {
        AnimationUtils.fadeOut(translateScrollView);
        spinner.setVisibility(View.VISIBLE);
    }

    //endregion

    //region onClick

    @OnClick(R.id.translate_swap_languages)
    public void onSwapLanguagesClick(View v) {
        if (!languageRepository.swapLanguages()) return;

        int tempPosition = destinationLanguageSpinner.getSelectedItemPosition() + 1;
        destinationLanguageSpinner.setSelection(targetLanguageSpinner.getSelectedItemPosition() - 1);
        targetLanguageSpinner.setSelection(tempPosition);

        if (translationRepository.getTranslation() == null) return;
        translateInputEditText.setText(translationRepository.getTranslation().getTranslatedText());
    }

    @OnClick(R.id.translate_action_favorite)
    public void onFavoriteClick(View v) {
        translationRepository.getTranslation().switchFavorite();
        changeFavoriteAction();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                HistorySqlService.update(translationRepository.getTranslation());
            }
        });
        t.start();
    }

    @OnClick(R.id.translate_action_copy)
    public void onCopyClick(View v) {
        ShareUtils.copyToClipboard(translationRepository.getTranslation().getTranslatedText());

        Toast
                .makeText(getContext(), R.string.translate_toast_text_was_copied, Toast.LENGTH_SHORT)
                .show();
    }

    @OnClick(R.id.translate_action_share)
    public void onShareClick(View v) {
        startActivity(ShareUtils.shareToAnotherApp(
                translationRepository.getTranslation().getTranslatedText()
        ));
    }

    @OnClick(R.id.translate_clear_button)
    public void onClearClick(View v) {
        translateInputEditText.setText("");
    }

    //endregion

    //region service callbacks

    @Override
    public void onStopTyping() {
        if (!validateForm()) return;
        String textToTranslate = translateInputEditText.getText().toString().trim();
        if (TextUtils.isEmpty(textToTranslate)) return;

        showSpinner();

        activeQuery = translate(
                textToTranslate,
                languageRepository.obtainCortege(),
                new HttpResultListener() {
                    @Override
                    public <T> void onHttpSuccess(T result) {
                        if (result instanceof TranslationModel) {
                            saveTranslationResponse((TranslationModel) result);
                            loadDictionary();
                        }
                    }

                    @Override
                    public void onHttpError(HttpError error) {
                        showErrorDialog();
                    }
                });
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        switch (adapterView.getId()) {
            case R.id.translate_target_language_spinner:
                if (!SpinnerUtils.isSelectedByUser(targetLanguageSpinner)) return;
                languageRepository
                        .setTargetFromLongName(targetLanguagesList[i])
                        .saveTargetLanguage();
                break;
            case R.id.translate_destination_language_spinner:
                if (!SpinnerUtils.isSelectedByUser(destinationLanguageSpinner)) return;
                languageRepository
                        .setDestinationFromLongName(allAvailableLanguagesList[i])
                        .saveDestinationLanguage();
                break;
        }

        stopTypingDetector.notifyDataChanged(translateInputEditText.getEditableText());
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    @Override
    public void onShowCloseButton() {
        clearTranslateView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onHideCloseButton() {
        clearTranslateView.setVisibility(View.GONE);
    }

    @Override
    public boolean isShown() {
        return clearTranslateView.getVisibility() == View.VISIBLE;
    }

    //endregion

    // region http

    private void saveTranslationResponse(TranslationModel translationModel) {
        TranslationModel translation = HistorySqlService.saveTranslation(translationModel);
        translationRepository.setTranslation(translation);
    }

    private void loadDictionary() {
        TranslationModel translation = translationRepository.getTranslation();

        if (!isDictionary(translation)) {
            setupTranslationView();
            return;
        }

        lookup(translation.getOriginalText(),
                translation.getLanguage(),
                new HttpResultListener() {
                    @Override
                    public <T> void onHttpSuccess(T result) {
                        if (result instanceof DictionaryModel) {
                            saveDictionaryResponse((DictionaryModel) result);
                            setupTranslationAndDictionaryViews();
                        }
                    }

                    @Override
                    public void onHttpError(HttpError error) {
                        setupTranslationView();
                    }
                });
    }

    private void saveDictionaryResponse(DictionaryModel dictionaryModel) {
        translationRepository.setDictionary(dictionaryModel);
    }

    //endregion

    //region lifecycle

    @Override
    public void onPause() {
        super.onPause();
        if (activeQuery != null && activeQuery.isExecuted()) activeQuery.cancel();
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (activeQuery != null && activeQuery.isExecuted()) activeQuery.cancel();
        if (!translationRepository.isEmpty()) {
            listener.onTranslationChanged(translationRepository.getTranslation(),
                    translationRepository.getDictionary());
        }
    }

    //endregion

    //region utils

    private boolean validateForm() {
        if (translateInputEditText.getEditableText().length() > TRANSLATION_LIMIT) {
            symbolCounterTextView.setTextColor(redColor);
            return false;
        }

        symbolCounterTextView.setTextColor(greyColor);
        return true;
    }

    private void showErrorDialog() {
        AnimationUtils.fadeOut(spinner);

        new MaterialDialog.Builder(getActivity())
                .title(R.string.error_dialog_title)
                .content(R.string.error_dialog_content)
                .positiveText(R.string.error_dialog_ok)
                .show();
    }

    private void setupTranslationView() {
        TranslationModel translation = translationRepository.getTranslation();
        if (translation == null) return;

        changeFavoriteAction();

        translationLanguageHolderTitleTextView.setText(languageRepository.getDestinationLanguage().name());
        primaryTranslationTextView.setText(translation.getTranslatedText());
        showTranslationViews(false);
    }

    private void setupTranslationAndDictionaryViews() {
        TranslationModel translation = translationRepository.getTranslation();
        if (translation == null) return;

        changeFavoriteAction();

        translationLanguageHolderTitleTextView.setText(languageRepository.getDestinationLanguage().name());
        primaryTranslationTextView.setText(translation.getTranslatedText());

        List<TranslationDictionaryModel> alternativeTranslations = aggregateAlternativeTranslations();
        if (alternativeTranslations.isEmpty()) {
            showTranslationViews(false);
            return;
        }

        alternativeTranslationAdapter.clear();
        alternativeTranslationAdapter.addTranslations(alternativeTranslations);
        recalculateAlternativeTranslationRVHeight();

        showTranslationViews(true);
    }

    private List<TranslationDictionaryModel> aggregateAlternativeTranslations() {
        List<DefinitionDictionaryModel> definitions = translationRepository.getDictionary().getDefinition();
        List<TranslationDictionaryModel> translations = new ArrayList<>();

        if (definitions == null || definitions.isEmpty()) return translations;

        for (DefinitionDictionaryModel definition : definitions) {
            translations.addAll(definition.getTranslation());
        }

        return translations;
    }

    private void changeFavoriteAction() {
        if (translationRepository.getTranslation().isFavorite())
            favoritesActionImageView.setImageResource(R.drawable.ic_favorite_white_24dp);
        else favoritesActionImageView.setImageResource(R.drawable.ic_favorite_border_white_24dp);
    }

    private void recalculateAlternativeTranslationRVHeight() {
        int count = alternativeTranslationAdapter.getItemCount();

        ViewGroup.LayoutParams params = alternativeTranslationRecyclerView.getLayoutParams();
        params.height = count * ALTERNATIVE_TRANSLATION_VIEW_HEIGHT;
        alternativeTranslationRecyclerView.setLayoutParams(params);
    }

    private boolean isDictionary(TranslationModel translationModel) {
        return !translateInputEditText.getEditableText().toString().trim().contains(SPACE);
    }

    //endregion
}
