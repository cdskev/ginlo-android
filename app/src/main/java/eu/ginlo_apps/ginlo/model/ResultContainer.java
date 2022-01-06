// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ResultContainer
        implements Parcelable {

    public static final Creator<ResultContainer> CREATOR = new Creator<ResultContainer>() {
        public ResultContainer createFromParcel(Parcel in) {
            return new ResultContainer(in);
        }

        public ResultContainer[] newArray(int size) {
            return new ResultContainer[size];
        }
    };

    public final int requestCode;

    public final int resultCode;

    public final Intent data;

    public ResultContainer(int requestCode,
                           int resultCode,
                           Intent data) {
        this.requestCode = requestCode;
        this.resultCode = resultCode;
        this.data = data;
    }

    private ResultContainer(Parcel in) {
        requestCode = in.readInt();
        resultCode = in.readInt();
        data = in.readParcelable(Intent.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest,
                              int flags) {
        dest.writeInt(requestCode);
        dest.writeInt(resultCode);
        dest.writeParcelable(data, flags);
    }
}
