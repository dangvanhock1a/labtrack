package com.pharmalab.labtrack.service;

import com.pharmalab.labtrack.domain.SampleStatus;

/** Chuyển trạng thái không có trong bảng chuyển của {@link SampleStatus} (BR-03). */
public class InvalidStatusTransitionException extends BusinessException {

	public InvalidStatusTransitionException(SampleStatus from, SampleStatus to) {
		super("INVALID_STATUS_TRANSITION", null,
				"Không thể chuyển mẫu từ \"" + from.getLabel() + "\" sang \"" + to.getLabel() + "\"");
	}

}
