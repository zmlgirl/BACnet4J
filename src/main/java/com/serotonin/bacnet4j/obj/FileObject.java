/*
 * ============================================================================
 * GNU General Public License
 * ============================================================================
 *
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * When signing a commercial license with Infinite Automation Software,
 * the following extension to GPL is made. A special exception to the GPL is
 * included to allow you to distribute a combined work that includes BAcnet4J
 * without being obliged to provide the source code for any proprietary components.
 *
 * See www.infiniteautomation.com for commercial license options.
 *
 * @author Matthew Lohbihler
 */
package com.serotonin.bacnet4j.obj;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.exception.BACnetRuntimeException;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.obj.fileAccess.FileAccess;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.ValueSource;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

/**
 * @author Matthew Lohbihler
 */
public class FileObject extends BACnetObject {
    /**
     * The actual file that this object represents.
     */
    private final FileAccess fileAccess;

    /**
     * The lock is used by AtomicReadFileRequest and AtomicWriteFileRequest services to lock the file object during
     * the course of the service, thus ensuring the "atomic" thing.
     */
    private final ReentrantLock lock = new ReentrantLock();

    public FileObject(final LocalDevice localDevice, final int instanceNumber, final String fileType,
            final FileAccess fileAccess) throws BACnetServiceException {
        super(localDevice, ObjectType.file, instanceNumber, fileAccess.getName());
        this.fileAccess = fileAccess;

        if (!fileAccess.exists())
            throw new BACnetRuntimeException("File does not exist");
        if (fileAccess.isDirectory())
            throw new BACnetRuntimeException("File is a directory");

        Objects.requireNonNull(fileType);
        Objects.requireNonNull(fileAccess);

        writePropertyInternal(PropertyIdentifier.fileType, new CharacterString(fileType));
        writePropertyInternal(PropertyIdentifier.fileAccessMethod, fileAccess.getAccessMethod());
        writePropertyInternal(PropertyIdentifier.archive, Boolean.FALSE);

        getProperty(PropertyIdentifier.fileSize);
        getProperty(PropertyIdentifier.modificationDate);
        getProperty(PropertyIdentifier.readOnly);
        getProperty(PropertyIdentifier.recordCount);
    }

    public ReentrantLock getLock() {
        return lock;
    }

    @Override
    protected void beforeGetProperty(final PropertyIdentifier pid, final UnsignedInteger propertyArrayIndex)
            throws BACnetServiceException {
        if (PropertyIdentifier.fileSize.equals(pid)) {
            set(PropertyIdentifier.fileSize, new UnsignedInteger(fileAccess.length()));
        } else if (PropertyIdentifier.modificationDate.equals(pid)) {
            set(PropertyIdentifier.modificationDate, new DateTime(fileAccess.lastModified()));
        } else if (PropertyIdentifier.readOnly.equals(pid)) {
            set(PropertyIdentifier.readOnly, fileAccess.canWrite() ? Boolean.FALSE : Boolean.TRUE);
        } else if (PropertyIdentifier.recordCount.equals(pid)) {
            if (fileAccess.hasRecordAccess())
                set(PropertyIdentifier.recordCount, new UnsignedInteger(fileAccess.recordCount()));
            else {
                throw new BACnetServiceException(ErrorClass.property, ErrorCode.readAccessDenied);
            }
        }
    }

    @Override
    protected boolean validateProperty(final ValueSource valueSource, final PropertyValue value)
            throws BACnetServiceException {
        if (PropertyIdentifier.fileSize.equals(value.getPropertyIdentifier())) {
            final UnsignedInteger fileSize = value.getValue();
            fileAccess.validateFileSizeWrite(fileSize.longValue());
        } else if (PropertyIdentifier.recordCount.equals(value.getPropertyIdentifier())) {
            final UnsignedInteger recordCount = value.getValue();
            fileAccess.validateRecordCountWrite(recordCount.longValue());
        }
        return false;
    }

    @Override
    protected void afterWriteProperty(final PropertyIdentifier pid, final Encodable oldValue,
            final Encodable newValue) {
        if (PropertyIdentifier.fileSize.equals(pid)) {
            fileAccess.writeFileSize(((UnsignedInteger) newValue).longValue());
        } else if (PropertyIdentifier.recordCount.equals(pid)) {
            fileAccess.writeRecordCount(((UnsignedInteger) newValue).longValue());
        }
    }

    public FileAccess getFileAccess() {
        return fileAccess;
    }
}
