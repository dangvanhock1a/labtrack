package com.pharmalab.labtrack.service;

import com.pharmalab.labtrack.domain.SampleStatus;

/** Chuyển trạng thái mẫu không nằm trong bảng chuyển của BR-03. */
public class InvalidStatusTransitionException extends BusinessRuleException {

	private final SampleStatus from;
	private final SampleStatus to;

	public InvalidStatusTransitionException(SampleStatus from, SampleStatus to) {
		super(null, "Không thể chuyển mẫu từ \"" + from.getLabel() + "\" sang \"" + to.getLabel() + "\"",
				"INVALID_STATUS_TRANSITION");
		this.from = from;
		this.to = to;
	}

	public SampleStatus getFrom() {
		return from;
	}

	public SampleStatus getTo() {
		return to;
	}

}
