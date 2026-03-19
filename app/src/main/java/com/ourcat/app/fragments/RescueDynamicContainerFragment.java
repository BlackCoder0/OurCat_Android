package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.ourcat.app.R;

public class RescueDynamicContainerFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rescue_dynamic_container, container, false);

        TabLayout tabLayout = view.findViewById(R.id.rescue_dynamic_tabs);
        ViewPager2 viewPager = view.findViewById(R.id.rescue_dynamic_pager);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return new RescueListFragment();
                } else if (position == 1) {
                    return new ForumFragment();
                }
                return new SquareFragment();
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText(getString(R.string.rescue_dynamic_tab_list));
            } else if (position == 1) {
                tab.setText(getString(R.string.rescue_dynamic_tab_discuss));
            } else {
                tab.setText(getString(R.string.rescue_dynamic_tab_square));
            }
        }).attach();

        return view;
    }
}
