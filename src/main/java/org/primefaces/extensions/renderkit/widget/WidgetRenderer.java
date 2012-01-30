/*
 * Copyright 2011-2012 PrimeFaces Extensions.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */

package org.primefaces.extensions.renderkit.widget;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.faces.FacesException;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import org.primefaces.component.api.Widget;

/**
 * Utilities for rendering with {@link Widget}s.
 *
 * @author Thomas Andraschko / last modified by $Author$
 * @version $Revision$
 * @since 0.3
 */
public class WidgetRenderer {

	private static final Map<String, OptionContainer[]> PROPERTY_KEYS_CACHE = new HashMap<String, OptionContainer[]>();

	//TODO addtional Options as param
	public static void renderWidgetScript(final FacesContext context, final String clientId, final ResponseWriter writer,
			final Widget widget, final boolean hasStyleSheet) throws IOException {

		final Class<?> widgetClass = widget.getClass();
		final String widgetName = widgetClass.getSimpleName();
		final String widgetVar = widget.resolveWidgetVar();

		writer.write("$(function() {");
		writer.write("PrimeFacesExt.cw('" + widgetName + "', '" + widgetVar + "', {");

		renderOptions(clientId, writer, widget);

		writer.write("}, " + hasStyleSheet + ");});");
    }

	public static final void renderOptions(final String clientId, final ResponseWriter writer,
			final Widget widget) throws IOException {

		final Class<?> widgetClass = widget.getClass();

		writer.write("id:'" + clientId + "'");

		final OptionContainer[] optionContainers = getOptionsFromPropertyKeys(widgetClass);
		for (int i = 0; i < optionContainers.length; i++) {
			final OptionContainer optionContainer = optionContainers[i];
			final Object propertyValue = getPropertValue(optionContainer, widget);

			renderOption(writer, optionContainer, propertyValue);
		}
	}

	public static void renderOption(final ResponseWriter writer, final OptionContainer optionContainer, final Object value) throws IOException {

		if (value != null) {
			if (String.class.isAssignableFrom(value.getClass()) ||
				Character.class.isAssignableFrom(value.getClass())) {

				//don't add quotes for objects or arrays
				final String stringValue = ((String) value).trim();
				if (stringValue.startsWith("{") || stringValue.startsWith("[")) {
					if (optionContainer.isEscape()) {
						writer.writeText("," + optionContainer.getName() + ":" + stringValue, null);
					} else {
						writer.write("," + optionContainer.getName() + ":" + stringValue);
					}
				} else {
					if (optionContainer.isUseDoubleQuotes()) {
						if (optionContainer.isEscape()) {
							writer.writeText("," + optionContainer.getName() + ":\"" + stringValue + "\"", null);
						} else {
							writer.write("," + optionContainer.getName() + ":\"" + stringValue + "\"");
						}
					} else {
						if (optionContainer.isEscape()) {
							writer.writeText("," + optionContainer.getName() + ":'" + stringValue + "'", null);
						} else {
							writer.write("," + optionContainer.getName() + ":'" + stringValue + "'");
						}
					}
				}
			} else {
				if (optionContainer.isEscape()) {
					writer.writeText("," + optionContainer.getName() + ":" + value, null);
				} else {
					writer.write("," + optionContainer.getName() + ":" + value);
				}
			}
		}
	}

    private static Object getPropertValue(final OptionContainer optionContainer, final Widget widget) {
    	try {
     		return optionContainer.getReadMethod().invoke(widget);
    	} catch (Exception e) {
			throw new FacesException("Can not get value for property '" + optionContainer.getPropertyName()
					+ "' and widget '" + widget.getClass().getName() + "'", e);
    	}
    }

    private static OptionContainer[] getOptionsFromPropertyKeys(final Class<?> widgetClass) {
    	OptionContainer[] options;

    	//try from cache first
    	if (PROPERTY_KEYS_CACHE.containsKey(widgetClass.getName())) {
    		options = PROPERTY_KEYS_CACHE.get(widgetClass.getName());
    	} else {
    		synchronized (PROPERTY_KEYS_CACHE) {
    	    	if (PROPERTY_KEYS_CACHE.containsKey(widgetClass.getName())) {
    	    		options = PROPERTY_KEYS_CACHE.get(widgetClass.getName());
    	    	} else {
	    	    	Class<?> propertyKeysClass = null;

	    	    	//find child class named "PropertyKeys"
	    			final Class<?>[] childClasses = widgetClass.getDeclaredClasses();
	    			for (int i = 0; i < childClasses.length; i++) {
	    				final Class<?> childClass = childClasses[i];
	    				if (childClass.getSimpleName().equals("PropertyKeys")) {
	    					propertyKeysClass = childClass;
	    				}
	    			}

	    			if (propertyKeysClass == null) {
	    				throw new FacesException("Widget '" + widgetClass.getName() + "' does not has PropertyKeys class.");
	    			}

	    			final Enum<?>[] propertyKeys = (Enum<?>[]) propertyKeysClass.getEnumConstants();

	    			final ArrayList<OptionContainer> optionContainerList = new ArrayList<OptionContainer>();

	    			for (int i = 0; i < propertyKeys.length; i++) {
	    				final Enum<?> propertyKey = propertyKeys[i];

	    				try {
							final Field propertyKeyField = propertyKeysClass.getDeclaredField(propertyKey.name());

		    				if (propertyKeyField.isAnnotationPresent(Option.class)) {
		    					final OptionContainer optionContainer = new OptionContainer();
		    					final Option option = propertyKeyField.getAnnotation(Option.class);
		    					final String propertyKeyAsString = propertyKey.toString();
		    					final String name = option.name().equals("") ? propertyKeyAsString : option.name();

		    					optionContainer.setEscape(option.escape());
		    					optionContainer.setName(name);
		    					optionContainer.setUseDoubleQuotes(option.useDoubleQuotes());
		    					optionContainer.setPropertyName(propertyKeyAsString);
		    					optionContainer.setReadMethod(new PropertyDescriptor(propertyKeyAsString, widgetClass).getReadMethod());

		    					optionContainerList.add(optionContainer);
		    				}
						} catch (Exception e) {
							throw new FacesException(
									"Can not get property '" + propertyKey.name() + "' in '" + widgetClass.getName() + "'", e);
						}
	    			}

	    			//add to cache
	    	    	PROPERTY_KEYS_CACHE.put(widgetClass.getName(), optionContainerList.toArray(new OptionContainer[0]));

	    	    	options = PROPERTY_KEYS_CACHE.get(widgetClass.getName());
				}
    		}
    	}

    	return options;
    }
}
