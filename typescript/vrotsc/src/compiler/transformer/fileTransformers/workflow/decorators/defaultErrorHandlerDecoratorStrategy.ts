/*-
 * #%L
 * vrotsc
 * %%
 * Copyright (C) 2023 - 2024 VMware
 * %%
 * Build Tools for VMware Aria
 * Copyright 2023 VMware, Inc.
 *
 * This product is licensed to you under the BSD-2 license (the "License"). You may not use this product except in compliance with the BSD-2 License.
 *
 * This product may include a number of subcomponents with separate copyright notices and license terms. Your use of these subcomponents is subject to the terms and conditions of the subcomponent's license, as noted in the LICENSE file.
 * #L%
 */
import { Decorator, MethodDeclaration, SourceFile } from "typescript";
import { StringBuilderClass } from "../../../../../utilities/stringBuilder";
import { WorkflowItemDescriptor, WorkflowItemType } from "../../../../decorators";
import { getDecoratorProps } from "../../../helpers/node";
import { findTargetItem } from "../helpers/findTargetItem";
import CanvasItemDecoratorStrategy from "./canvasItemDecoratorStrategy";

// UI positioning constants in the output XML file.
const xBasePosition = 180;
const yBasePosition = 110;
const offSet = 20;

/**
 * Responsible for printing out a default error handler
 * Important Note: The name of the error handler component should match the name of the end workflow element.
 * @example
 * ```xml
	<error-handler name="item1">
		<position x="415.0" y="65.40"/>
	</error-handler>
	<workflow-item name="item1" type="end" end-mode="0">
		<position x="405.0" y="55.40"/>
	</workflow-item>
 * ```
 */
export default class DefaultErrorHandlerDecoratorStrategy implements CanvasItemDecoratorStrategy {

	/**
	 * Return XML tag for the error handler workflow item.
	 *
	 * @returns XML tag name.
	 */
	public getCanvasType(): string {
		return "error-handler";
	}

	/**
	 * Return the workflow item type supported by this decorator.
	 *
	 * @returns type of the workflow element.
	 */
	public getDecoratorType(): WorkflowItemType {
		return WorkflowItemType.DefaultErrorHandler;
	}

	/**
	 * Register the canvas item arguments. For the default error handler only "target" and "exception" are supported.
	 *
	 * @param itemInfo item info for that properties should be fetched.
	 * @param decoratorNode decorator node handle.
	 */
	public registerItemArguments(itemInfo: WorkflowItemDescriptor, decoratorNode: Decorator): void {
		const decoratorProperties = getDecoratorProps(decoratorNode);
		if (!decoratorProperties?.length) {
			return;
		}
		decoratorProperties.forEach((propTuple) => {
			const [propName, propValue] = propTuple;
			switch (propName) {
				case "target": {
					itemInfo.target = propValue;
					break;
				}
				case "exceptionVariable": {
					itemInfo.canvasItemPolymorphicBag.exceptionVariable = propValue;
					break;
				}
				default: {
					throw new Error(`Item attribute '${propName}' is not supported for ${this.getDecoratorType()} item`);
				}
			}
		});
	}

	/**
	 * There is no need to print the source file for the default error handler.
	 */
	public printSourceFile(methodNode: MethodDeclaration, sourceFile: SourceFile, itemInfo: WorkflowItemDescriptor): string {
		return "";
	}

	/**
	 * Prints out the default handler item. Note that it needs to be connected with an end item and
	 * both must have identical name.
	 *
	 * @param itemInfo The item to print.
	 * @param pos The position of the item in the workflow.
	 *
	 * @returns The string representation of the item.
	 */
	public printItem(itemInfo: WorkflowItemDescriptor, pos: number): string {
		const stringBuilder = new StringBuilderClass("", "");

		const targetItemName = findTargetItem(itemInfo.target, pos, itemInfo);
		if (targetItemName === null) {
			throw new Error(`Unable to find target item for ${this.getDecoratorType()} item`);
		}
		const exceptionVariable = itemInfo?.canvasItemPolymorphicBag?.exceptionVariable;
		// it is important that the name of the error handler should be the same as the pointing target item name
		stringBuilder.append(`<error-handler name="${targetItemName}" `);
		if (exceptionVariable) {
			stringBuilder.append(`throw-bind-name="${exceptionVariable}" `);
		}
		stringBuilder.append(">").appendLine();
		stringBuilder.indent();
		stringBuilder.append(`<position x="${xBasePosition + offSet * pos}" y="${yBasePosition}"/>`).appendLine();
		stringBuilder.unindent();
		stringBuilder.append("</error-handler>").appendLine();
		stringBuilder.unindent();

		return stringBuilder.toString();
	}
}
