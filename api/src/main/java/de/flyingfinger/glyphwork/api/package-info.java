/**
 * Glyphwork's API: scenes made of elements, drawn as pixel GUIs inside vanilla dialogs.
 *
 * <p>Get the service with {@link de.flyingfinger.glyphwork.api.Glyphwork#get()}, build a
 * {@link de.flyingfinger.glyphwork.api.Scene} and open it. Everything in this package is non-null
 * unless marked {@code @Nullable}. Methods marked {@code @ApiStatus.Internal} are used by Glyphwork
 * itself to draw the elements and are not covered by compatibility promises; {@code @ApiStatus.Experimental}
 * marks parts that may still change in a minor version. New element types and form fields may be
 * added in minor versions, so a switch over {@link de.flyingfinger.glyphwork.api.Element} or
 * {@link de.flyingfinger.glyphwork.api.Form.Field} should have a default branch.
 */
@NullMarked
package de.flyingfinger.glyphwork.api;

import org.jspecify.annotations.NullMarked;
