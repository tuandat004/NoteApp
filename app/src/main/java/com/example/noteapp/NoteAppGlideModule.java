package com.example.noteapp;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

/**
 * Glide AppGlideModule — required to suppress "Failed to find GeneratedAppGlideModule" warning.
 * Enables Glide's annotation processor to generate GlideApp / AppGlideModule integration.
 */
@GlideModule
public class NoteAppGlideModule extends AppGlideModule {
    // No custom config needed — default Glide behavior is sufficient
}
