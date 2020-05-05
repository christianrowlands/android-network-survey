package com.craxiom.networksurvey.listeners;

import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.craxiom.networksurvey.R;

/**
 * An {@link android.view.View.OnClickListener} for the {@link R.layout#expandable_help_card} that handles expanding
 * and collapsing the view.
 *
 * @since 0.1.1
 */
public class HelpCardListener implements View.OnClickListener
{
    private final View view;

    /**
     * @param view                      The view that contains the {@link R.layout#expandable_help_card}.
     * @param helpDescriptionResourceId The String resource ID that contains the help description to place in the help
     *                                  card.
     */
    public HelpCardListener(View view, int helpDescriptionResourceId)
    {
        this.view = view;
        final TextView connectionDescriptionText = view.findViewById(R.id.help_description);
        connectionDescriptionText.setText(helpDescriptionResourceId);
    }

    @Override
    public void onClick(View v)
    {
        final TextView connectionDescriptionText = view.findViewById(R.id.help_description);
        final ToggleButton expandArrow = view.findViewById(R.id.expand_toggle_button);
        if (connectionDescriptionText.getVisibility() == View.VISIBLE)
        {
            connectionDescriptionText.setVisibility(View.GONE);
            expandArrow.setChecked(false);
        } else
        {
            connectionDescriptionText.setVisibility(View.VISIBLE);
            expandArrow.setChecked(true);
        }
    }
}
