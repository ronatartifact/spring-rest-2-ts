package com.blueveery.springrest2ts.tsmodel;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by tomek on 08.08.17.
 */
public abstract class TSScopedType extends TSType implements ICommentedElement, IAnnotated{
    protected TSModule module;
    protected TSComment tsComment = new TSComment("ComplexTypeComment");
    private List<Annotation> annotationList = new ArrayList<>();
    private Set<Class> mappedFromSet = new HashSet<>();

    protected TSScopedType(String name, TSModule module) {
        super(name);
        this.module = module;
    }

    public TSModule getModule() {
        return module;
    }

    @Override
    public TSComment getTsComment() {
        return tsComment;
    }

    public List<Annotation> getAnnotationList() {
        return annotationList;
    }

    public Set<Class> getMappedFromSet() {
        return mappedFromSet;
    }
}
