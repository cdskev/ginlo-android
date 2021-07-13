// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import android.graphics.Color;

import androidx.core.content.res.ResourcesCompat;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.CompanyLayoutModel;

/**
 * Created by SGA on 06.03.2017.
 */

public class CompanyLayoutDeserializer
        implements JsonDeserializer<CompanyLayoutModel>
{
   @Override
   public CompanyLayoutModel deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
           throws JsonParseException
   {

      final CompanyLayoutModel    companyLayoutModel = new CompanyLayoutModel();
      final JsonObject jsonObject = jsonElement.getAsJsonObject();
      final SimsMeApplication simsMeApplication = SimsMeApplication.getInstance();
      IllegalArgumentException exception = null;
      StringBuilder sb = new StringBuilder("CompanyLayoutDeserializer: wrong values: ");

      companyLayoutModel.defaultSettings = true;
      if (jsonObject.has("mainColor"))
      {
         final String mainColor = jsonObject.get("mainColor").getAsString();
         try
         {
            companyLayoutModel.mainColor = Color.parseColor(mainColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.main, null) != companyLayoutModel.mainColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("mainColor, ");
         }
      }
      if (jsonObject.has("mainContrastColor"))
      {
         final String mainContrastColor = jsonObject.get("mainContrastColor").getAsString();
         try
         {
            companyLayoutModel.mainContrastColor = Color.parseColor(mainContrastColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.mainContrast, null) != companyLayoutModel.mainContrastColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("mainContrastColor, ");
         }
      }
      if (jsonObject.has("actionColor"))
      {
         final String actionColor = jsonObject.get("actionColor").getAsString();
         try
         {
            companyLayoutModel.actionColor = Color.parseColor(actionColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.action, null) != companyLayoutModel.actionColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("actionColor, ");
         }
      }
      if (jsonObject.has("actionContrastColor"))
      {
         final String actionContrastColor = jsonObject.get("actionContrastColor").getAsString();
         try
         {
            companyLayoutModel.actionContrastColor = Color.parseColor(actionContrastColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.actionContrast, null) != companyLayoutModel.actionContrastColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("actionContrastColor, ");
         }
      }
      if (jsonObject.has("mediumColor"))
      {
         final String mediumColor = jsonObject.get("mediumColor").getAsString();
         try
         {
            companyLayoutModel.mediumColor = Color.parseColor(mediumColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.medium, null) != companyLayoutModel.mediumColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("mediumColor, ");
         }
      }
      if (jsonObject.has("mediumContrastColor"))
      {
         final String mediumContrastColor = jsonObject.get("mediumContrastColor").getAsString();
         try
         {
            companyLayoutModel.mediumContrastColor = Color.parseColor(mediumContrastColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.mediumContrast, null) != companyLayoutModel.mediumContrastColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("mediumContrastColor, ");
         }
      }
      if (jsonObject.has("highColor"))
      {
         final String highColor = jsonObject.get("highColor").getAsString();
         try
         {
            companyLayoutModel.highColor = Color.parseColor(highColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.secure, null) != companyLayoutModel.highColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("highColor, ");
         }
      }
      if (jsonObject.has("highContrastColor"))
      {
         final String highContrastColor = jsonObject.get("highContrastColor").getAsString();
         try
         {
            companyLayoutModel.highContrastColor = Color.parseColor(highContrastColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.secureContrast, null) != companyLayoutModel.highContrastColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("highContrastColor, ");
         }
      }
      if (jsonObject.has("lowColor"))
      {
         final String lowColor = jsonObject.get("lowColor").getAsString();
         try
         {
            companyLayoutModel.lowColor = Color.parseColor(lowColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.insecure, null) != companyLayoutModel.lowColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("lowColor, ");
         }
      }
      if (jsonObject.has("lowContrastColor"))
      {
         final String lowContrastColor = jsonObject.get("lowContrastColor").getAsString();
         try
         {
            companyLayoutModel.lowContrastColor = Color.parseColor(lowContrastColor);
            if(ResourcesCompat.getColor(simsMeApplication.getResources(), R.color.insecureContrast, null) != companyLayoutModel.lowContrastColor) {
               companyLayoutModel.defaultSettings = false;
            }
         }
         catch (IllegalArgumentException e)
         {
            exception = e;
            sb.append("lowContrastColor, ");
         }
      }

      if (exception != null)
      {
         LogUtil.e(this.getClass().getSimpleName(), exception.getMessage(), exception);
      }

      return companyLayoutModel;
   }
}
