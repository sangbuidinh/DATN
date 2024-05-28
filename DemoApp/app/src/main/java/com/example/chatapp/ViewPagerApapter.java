package com.example.chatapp;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;


import java.util.ArrayList;


public class ViewPagerApapter extends FragmentStateAdapter {
    private final ArrayList<Fragment> fragments;
    private final ArrayList<String> titles;
    public ViewPagerApapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        this.fragments = new ArrayList<>();
        this.titles = new ArrayList<>();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments.get(position);
    }

    //da co
    @Override
    public int getItemCount() {
        return fragments.size();
    }
    public void addFragment(Fragment fragment, String title){
        fragments.add(fragment);
        titles.add(title);
    }
    public CharSequence getPageTitle(int position){
        return titles.get(position);
    }

}
