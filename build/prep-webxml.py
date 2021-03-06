#!/usr/bin/env python
namespaces = {
        'prod'    : { 'suffix' : 'prod',    'namespace' : '' },
        'staging' : { 'suffix' : 'staging', 'namespace' : 'staging' },
}

def get_branch():
    import os
    return os.popen("git symbolic-ref -q HEAD") \
             .read() \
             .rstrip() \
             .split('/')[-1]

def main():
    src = 'war/WEB-INF/appengine-web-template.xml'
    target = 'war/WEB-INF/appengine-web.xml'
    branch = get_branch()
    try:
        bindings = namespaces[get_branch()]
    except KeyError:
        bindings = { 'suffix'    : 'dev',
                     'namespace' : 'dev' }

    def format(line):
        return line.format(**bindings)
    with open(src, 'r') as src_f:
        with open(target, 'w') as target_f:
            target_f.write('<!-- Autogenerated file do not edit. Edit %s and run "make all". -->\n' % src)
            target_f.writelines(map(format, src_f.readlines()))

    print 'Updating appengine-web.xml with for branch: "%s" bindings: "%s"' % \
          (branch, bindings)

if __name__ == '__main__':
    main();
